package com.example.serverprovision.domain.os.service.extractor;

import com.example.serverprovision.domain.os.model.enums.OSName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

// Rocky Linux / CentOS (RHEL 계열) comps.xml 기반 추출기
// 흐름: repomd.xml → comps 파일 위치 → comps.xml(.gz) 파싱
@Slf4j
@Component
@RequiredArgsConstructor
public class RHELCompsExtractor implements CompsExtractorStrategy {

    private static final List<OSName> SUPPORTED = List.of(OSName.ROCKY_LINUX, OSName.CENTOS);

    private final RestClient restClient;

    @Override
    public boolean supports(OSName osName) {
        return SUPPORTED.contains(osName);
    }

    // 로컬 탐색 시 repodata 를 찾지 않아도 되는 대용량/비repo 디렉토리
    private static final Set<String> LOCAL_SKIP_DIRS = Set.of(
            "Packages", "images", "isolinux", "EFI", "LiveOS", "postinstall"
    );

    // HTTP URL 탐색용 알려진 경로 패턴 목록
    // 로컬 경로와 달리 HTTP 는 디렉토리 목록 조회가 불가능하므로 고정 목록 유지
    private static final List<String> HTTP_REPODATA_CANDIDATES = List.of(
            "",                 // CentOS/RHEL 7 — ISO 루트
            "/AppStream",       // RHEL/Rocky/AlmaLinux 8, 9 — comps 주 위치
            "/BaseOS",          // RHEL/Rocky/AlmaLinux 8, 9
            "/PowerTools",      // CentOS 8 Stream
            "/CRB",             // Rocky 9, AlmaLinux 9 (PowerTools 후속)
            "/extras",          // CentOS 7/8 Extras
            "/HighAvailability",// RHEL HA
            "/Server"           // 일부 구형 배포판 레이아웃
    );

    @Override
    public CompsExtractionResult extract(String isoMountPath) {
        // 경로 끝 슬래시 정규화
        String basePath = isoMountPath.endsWith("/")
                ? isoMountPath.substring(0, isoMountPath.length() - 1)
                : isoMountPath;

        log.info("[RHELCompsExtractor] 추출 시작. isoMountPath={}", basePath);

        // 1. comps 데이터를 포함한 모든 repodata 위치 탐색
        //    Rocky/RHEL 계열은 환경 정의가 AppStream, BaseOS, PowerTools/CRB 등에 분산될 수 있다.
        List<String> effectiveBases = findAllEffectiveBases(basePath);
        if (effectiveBases.isEmpty()) {
            boolean isHttp = basePath.startsWith("http://") || basePath.startsWith("https://");
            String detail = isHttp
                    ? "시도한 HTTP 경로: " + HTTP_REPODATA_CANDIDATES
                    : "탐색 루트: " + basePath + " (최대 깊이 3), 건너뛴 디렉토리: " + LOCAL_SKIP_DIRS;
            throw new IllegalStateException(
                    "comps 데이터를 포함한 repomd.xml을 찾을 수 없습니다. basePath=" + basePath
                            + " | " + detail);
        }

        // 2. 각 repo 의 comps.xml 을 파싱하여 결과 병합 (환경·그룹 중복 제거)
        Map<String, CompsExtractionResult.GroupData> mergedGroups = new LinkedHashMap<>();
        List<CompsExtractionResult.EnvironmentData> mergedEnvironments = new ArrayList<>();
        Set<String> seenEnvCodes = new LinkedHashSet<>();

        for (String effectiveBase : effectiveBases) {
            log.info("[RHELCompsExtractor] repo 파싱 중. repoBase={}", effectiveBase);
            CompsExtractionResult partial = extractFromBase(effectiveBase);

            // 그룹 병합: 먼저 등장한 정의 우선 (putIfAbsent)
            partial.allGroups().forEach(mergedGroups::putIfAbsent);

            // 환경 병합: 동일 환경 코드 중복 제외
            for (CompsExtractionResult.EnvironmentData env : partial.environments()) {
                if (seenEnvCodes.add(env.environmentCode())) {
                    mergedEnvironments.add(env);
                }
            }
        }

        log.info("[RHELCompsExtractor] 병합 완료. 환경={}, 그룹={}", mergedEnvironments.size(), mergedGroups.size());
        return new CompsExtractionResult(mergedEnvironments, mergedGroups);
    }

    // comps 데이터를 포함한 repomd.xml 이 있는 모든 상위 디렉토리 경로 목록 반환.
    // 로컬 경로와 HTTP URL 을 자동 분기 처리한다.
    private List<String> findAllEffectiveBases(String basePath) {
        if (basePath.startsWith("http://") || basePath.startsWith("https://")) {
            return findBasesHttp(basePath);
        }
        return findBasesLocal(basePath);
    }

    // 로컬 파일 시스템에서 Files.walk 로 repodata 디렉토리를 재귀 탐색.
    // RHEL 버전별 구조 차이(루트/AppStream/BaseOS/PowerTools/CRB 등)를 자동으로 처리한다.
    private List<String> findBasesLocal(String basePath) {
        Path root = Paths.get(basePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("로컬 ISO 경로가 디렉토리가 아닙니다: " + basePath);
        }

        try (Stream<Path> stream = Files.walk(root, 3)) {
            List<String> result = stream
                    // repodata 이름 디렉토리 매칭
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && "repodata".equals(p.getFileName().toString()))
                    // Packages/ 등 제외 디렉토리 내부면 무시
                    .filter(p -> !shouldSkip(p))
                    // repodata 의 부모 디렉토리 = effectiveBase
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .map(p -> p.toAbsolutePath().toString())
                    // comps(group) 데이터 포함 여부 검증
                    .filter(this::hasGroupData)
                    .distinct()
                    .collect(Collectors.toList());

            result.forEach(p ->
                    log.info("[RHELCompsExtractor] 로컬 comps 포함 repo 발견. path={}/repodata/repomd.xml", p));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "로컬 ISO 디렉토리 탐색 실패 (권한 또는 파일시스템 문제 가능): " + basePath, e);
        }
    }

    // HTTP URL 은 디렉토리 목록 조회가 불가능하므로, 알려진 경로 패턴을 순차 시도한다.
    private List<String> findBasesHttp(String basePath) {
        List<String> result = new ArrayList<>();
        for (String suffix : HTTP_REPODATA_CANDIDATES) {
            String candidate = basePath + suffix;
            if (hasGroupData(candidate)) {
                result.add(candidate);
                log.info("[RHELCompsExtractor] HTTP comps 포함 repo 발견. path={}/repodata/repomd.xml", candidate);
            } else {
                log.debug("[RHELCompsExtractor] HTTP 경로 없음 또는 comps 없음. path={}/repodata/repomd.xml", candidate);
            }
        }
        return result;
    }

    // repodata 경로의 부모 컴포넌트 중 LOCAL_SKIP_DIRS 에 속한 이름이 있으면 true.
    // 예: /mnt/iso/Packages/xxx/repodata → Packages 포함 → 제외
    private boolean shouldSkip(Path repoDataPath) {
        for (Path component : repoDataPath) {
            if (LOCAL_SKIP_DIRS.contains(component.toString())) {
                return true;
            }
        }
        return false;
    }

    // {base}/repodata/repomd.xml 이 존재하고 type="group" 항목을 포함하는지 확인
    private boolean hasGroupData(String base) {
        try {
            return fetchText(base + "/repodata/repomd.xml").contains("type=\"group\"");
        } catch (Exception e) {
            return false;
        }
    }

    // 단일 repodata 루트에서 comps.xml 다운로드 및 파싱
    private CompsExtractionResult extractFromBase(String effectiveBase) {
        // repomd.xml 에서 comps 파일 위치 조회
        String repomdContent = fetchText(effectiveBase + "/repodata/repomd.xml");
        String compsHref = parseCompsHref(repomdContent);
        log.info("[RHELCompsExtractor] comps 파일 위치 확인. href={}", compsHref);

        // comps 파일 다운로드 (절대 경로면 그대로, 상대 경로면 effectiveBase 결합)
        String compsUrl = compsHref.startsWith("http") ? compsHref : effectiveBase + "/" + compsHref;
        byte[] compsBytes = fetchBytes(compsUrl);

        // GZip 압축 해제 (필요한 경우)
        InputStream compsStream = compsHref.endsWith(".gz")
                ? decompress(compsBytes)
                : new ByteArrayInputStream(compsBytes);

        // comps.xml 파싱
        Document doc = parseXml(compsStream);
        Map<String, CompsExtractionResult.GroupData> groups = parseGroups(doc);
        List<CompsExtractionResult.EnvironmentData> environments = parseEnvironments(doc);

        log.debug("[RHELCompsExtractor] 단일 repo 파싱 완료. effectiveBase={}, 환경={}, 그룹={}",
                effectiveBase, environments.size(), groups.size());
        return new CompsExtractionResult(environments, groups);
    }

    // repomd.xml 에서 <data type="group"><location href="..."/> 추출
    private String parseCompsHref(String repomdXml) {
        Document doc = parseXml(new ByteArrayInputStream(repomdXml.getBytes()));
        NodeList dataNodes = doc.getElementsByTagName("data");
        for (int i = 0; i < dataNodes.getLength(); i++) {
            Element data = (Element) dataNodes.item(i);
            if ("group".equals(data.getAttribute("type"))) {
                NodeList locations = data.getElementsByTagName("location");
                if (locations.getLength() > 0) {
                    return ((Element) locations.item(0)).getAttribute("href");
                }
            }
        }
        throw new IllegalStateException("repomd.xml 에서 comps 파일 위치를 찾을 수 없습니다.");
    }

    // comps.xml 의 모든 <group> 원소 파싱 → groupId → GroupData 맵
    private Map<String, CompsExtractionResult.GroupData> parseGroups(Document doc) {
        Map<String, CompsExtractionResult.GroupData> result = new LinkedHashMap<>();
        NodeList groupNodes = doc.getElementsByTagName("group");
        for (int i = 0; i < groupNodes.getLength(); i++) {
            Element group = (Element) groupNodes.item(i);
            String id = textContent(group, "id");
            if (id == null || id.isBlank()) continue;

            result.put(id, new CompsExtractionResult.GroupData(
                    id,
                    defaultLangName(group),
                    textContent(group, "description"),
                    "true".equals(textContent(group, "default"))
            ));
        }
        return result;
    }

    // comps.xml 의 모든 <environment> 원소 파싱
    private List<CompsExtractionResult.EnvironmentData> parseEnvironments(Document doc) {
        List<CompsExtractionResult.EnvironmentData> result = new ArrayList<>();
        NodeList envNodes = doc.getElementsByTagName("environment");
        for (int i = 0; i < envNodes.getLength(); i++) {
            Element env = (Element) envNodes.item(i);
            String id = textContent(env, "id");
            if (id == null || id.isBlank()) continue;

            // <grouplist> + <optionlist> 의 모든 groupid 수집
            List<String> groupCodes = new ArrayList<>();
            collectGroupIds(env, "grouplist", groupCodes);
            collectGroupIds(env, "optionlist", groupCodes);

            result.add(new CompsExtractionResult.EnvironmentData(
                    "^" + id,                                          // kickstart @^ 접두어 형식
                    defaultLangName(env),
                    textContent(env, "description"),
                    "true".equals(textContent(env, "default")),
                    groupCodes
            ));
        }
        return result;
    }

    // 지정 태그 하위의 <groupid> 텍스트를 targetList 에 추가
    private void collectGroupIds(Element parent, String containerTag, List<String> targetList) {
        NodeList containers = parent.getElementsByTagName(containerTag);
        if (containers.getLength() == 0) return;
        Element container = (Element) containers.item(0);
        NodeList groupIds = container.getElementsByTagName("groupid");
        for (int i = 0; i < groupIds.getLength(); i++) {
            String code = groupIds.item(i).getTextContent().trim();
            if (!code.isBlank() && !targetList.contains(code)) {
                targetList.add(code);
            }
        }
    }

    // xml:lang 속성 없는 <name> 원소의 텍스트 (기본 영문명) 반환
    private String defaultLangName(Element parent) {
        NodeList names = parent.getElementsByTagName("name");
        for (int i = 0; i < names.getLength(); i++) {
            Element nameEl = (Element) names.item(i);
            // xml:lang 속성이 없거나 비어 있는 것이 기본 이름
            String lang = nameEl.getAttribute("xml:lang");
            if (lang == null || lang.isBlank()) {
                return nameEl.getTextContent().trim();
            }
        }
        return "";
    }

    // 직계 자식 태그의 텍스트 반환
    private String textContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent().trim();
    }

    // XML 문자열 → DOM Document 파싱 (DTD 다운로드 비활성화)
    private Document parseXml(InputStream input) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 DTD 참조 비활성화 (comps.xml 의 DOCTYPE 처리 방지)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (Exception e) {
            throw new IllegalStateException("XML 파싱 실패: " + e.getMessage(), e);
        }
    }

    // GZip 압축 해제
    private InputStream decompress(byte[] compressed) {
        try {
            return new GZIPInputStream(new ByteArrayInputStream(compressed));
        } catch (Exception e) {
            throw new IllegalStateException("GZip 압축 해제 실패: " + e.getMessage(), e);
        }
    }

    // HTTP URL 또는 로컬 경로에서 텍스트 수신
    private String fetchText(String path) {
        return new String(fetchBytes(path));
    }

    // HTTP URL 또는 로컬 파일에서 바이트 수신
    private byte[] fetchBytes(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            log.debug("[RHELCompsExtractor] HTTP 수신: {}", path);
            return restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(byte[].class);
        } else {
            log.debug("[RHELCompsExtractor] 로컬 파일 읽기: {}", path);
            try {
                return Files.readAllBytes(Paths.get(path));
            } catch (Exception e) {
                throw new IllegalStateException("로컬 파일 읽기 실패: " + path, e);
            }
        }
    }
}
