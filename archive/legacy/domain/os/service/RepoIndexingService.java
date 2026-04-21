package com.example.serverprovision.domain.os.service;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageRef;
import com.example.serverprovision.domain.os.entity.OSServiceRef;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.domain.os.repository.OSPackageRefRepository;
import com.example.serverprovision.domain.os.repository.OSServiceRefRepository;
import com.example.serverprovision.domain.os.service.IsoPreparationService.PreparedIsoPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
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

/**
 * OSMetadata 별 저장소 인덱스(패키지·서비스 참조) 생성 서비스.
 *
 * <p>주어진 OSMetadata 의 {@code isoMountPath} 아래에서 {@code repodata/*-primary.xml.gz} 및
 * {@code repodata/*-filelists.xml.gz} 를 찾아 파싱한다. Rocky 8/9/10 처럼 AppStream/BaseOS 등
 * 복수 repo 가 섞인 트리도 지원한다 (깊이 3 까지 탐색).</p>
 *
 * <p>파싱 전략:
 * <ul>
 *     <li>primary.xml: StAX streaming 으로 {@code <package><name>} 만 추출. 아키텍처별 중복 제거.</li>
 *     <li>filelists.xml: StAX streaming 으로 {@code <package name="..."><file>.../*.service</file>} 추출.
 *         파일이 수백 MB 까지 커질 수 있으므로 DOM 로 로드하지 않는다.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingService {

    /**
     * 진행률 콜백. 비동기 실행 시 {@link RepoIndexingTask} 에 단계·퍼센트를 전달한다.
     */
    @FunctionalInterface
    public interface ProgressReporter {
        void report(String stage, int progress);
        ProgressReporter NOOP = (stage, progress) -> {};
    }

    /** 인덱싱 결과 요약. */
    public record IndexingSummary(int packageCount, int serviceCount) {}

    // comps 추출과 동일한 건너뛰기 디렉토리.
    private static final Set<String> LOCAL_SKIP_DIRS = Set.of(
            "Packages", "images", "isolinux", "EFI", "LiveOS", "postinstall"
    );

    private final OSMetadataRepository osMetadataRepository;
    private final OSPackageRefRepository packageRefRepository;
    private final OSServiceRefRepository serviceRefRepository;
    private final IsoPreparationService isoPreparationService;

    /**
     * 주어진 OSMetadata 의 모든 repo 를 파싱하여 패키지/서비스 인덱스를 재생성한다.
     * 기존 인덱스 엔트리는 모두 삭제 후 재삽입된다 (전체 교체).
     */
    @Transactional
    public IndexingSummary indexAndSave(Long osMetadataId, ProgressReporter progress) {

        progress.report("OS 메타데이터 조회 중", 3);
        OSMetadata meta = osMetadataRepository.findById(osMetadataId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 OS 메타데이터입니다. id=" + osMetadataId));

        progress.report("ISO 경로 준비 중 (마운트/압축 해제)", 8);
        try (PreparedIsoPath prepared = isoPreparationService.prepare(meta.getIsoMountPath())) {

            progress.report("repodata 디렉토리 탐색 중", 15);
            List<String> repos = findAllRepoBases(prepared.effectivePath());
            if (repos.isEmpty()) {
                throw new IllegalStateException(
                        "repodata 디렉토리를 찾을 수 없습니다. path=" + prepared.effectivePath());
            }
            log.info("[RepoIndexingService] 발견된 repo 수={}, paths={}", repos.size(), repos);

            progress.report("기존 인덱스 삭제 중", 20);
            packageRefRepository.deleteAllByOsMetadataId(osMetadataId);
            serviceRefRepository.deleteAllByOsMetadataId(osMetadataId);

            // Package 와 service 를 repo 별로 순차 파싱하여 적재.
            Map<String, OSPackageRef> packages = new LinkedHashMap<>();  // name → entity (중복 제거)
            Map<String, OSServiceRef> services = new LinkedHashMap<>();

            int total = repos.size();
            for (int i = 0; i < total; i++) {
                String repoBase = repos.get(i);
                String repoLabel = deriveRepoLabel(prepared.effectivePath(), repoBase);
                int baseProgress = 25 + (int) (50.0 * i / total);

                progress.report("패키지 인덱스 파싱 중 (" + repoLabel + ")", baseProgress);
                String primaryHref = locateMetadataHref(repoBase, "primary");
                if (primaryHref != null) {
                    parsePrimary(repoBase + "/" + primaryHref, repoLabel, meta, packages);
                }

                progress.report("서비스 인덱스 파싱 중 (" + repoLabel + ")", baseProgress + 15);
                String filelistsHref = locateMetadataHref(repoBase, "filelists");
                if (filelistsHref != null) {
                    parseFilelists(repoBase + "/" + filelistsHref, meta, services);
                }
            }

            progress.report("DB 에 인덱스 저장 중 (" + packages.size() + "개 패키지)", 80);
            packageRefRepository.saveAll(packages.values());

            progress.report("DB 에 인덱스 저장 중 (" + services.size() + "개 서비스)", 92);
            serviceRefRepository.saveAll(services.values());

            progress.report("완료", 100);
            log.info("[RepoIndexingService] 인덱싱 완료. osMetadataId={}, pkg={}, svc={}",
                    osMetadataId, packages.size(), services.size());
            return new IndexingSummary(packages.size(), services.size());
        }
    }

    // =========================================================================
    // repodata 탐색
    // =========================================================================

    private List<String> findAllRepoBases(String basePath) {
        // HTTP URL 은 현재 인덱싱 대상이 아니므로 로컬만 지원. 필요 시 추후 확장.
        if (basePath.startsWith("http://") || basePath.startsWith("https://")) {
            throw new UnsupportedOperationException(
                    "HTTP URL 기반 ISO 는 인덱싱을 지원하지 않습니다. 로컬 마운트 경로를 사용하세요.");
        }

        Path root = Paths.get(basePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("ISO 경로가 디렉토리가 아닙니다: " + basePath);
        }

        try (Stream<Path> stream = Files.walk(root, 3)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && "repodata".equals(p.getFileName().toString()))
                    .filter(p -> !shouldSkip(p))
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .map(p -> p.toAbsolutePath().toString())
                    // repomd.xml 이 존재하는지로 유효 repo 여부 판별
                    .filter(p -> Files.exists(Paths.get(p, "repodata", "repomd.xml")))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("ISO 디렉토리 탐색 실패: " + basePath, e);
        }
    }

    private boolean shouldSkip(Path repoDataPath) {
        for (Path component : repoDataPath) {
            if (LOCAL_SKIP_DIRS.contains(component.toString())) {
                return true;
            }
        }
        return false;
    }

    /** repomd.xml 에서 주어진 type 의 데이터 파일 상대경로 반환. 없으면 null. */
    private String locateMetadataHref(String repoBase, String type) {
        Path repomd = Paths.get(repoBase, "repodata", "repomd.xml");
        try {
            byte[] bytes = Files.readAllBytes(repomd);
            Document doc = parseXmlDom(new ByteArrayInputStream(bytes));
            NodeList dataNodes = doc.getElementsByTagName("data");
            for (int i = 0; i < dataNodes.getLength(); i++) {
                Element data = (Element) dataNodes.item(i);
                if (type.equals(data.getAttribute("type"))) {
                    NodeList locs = data.getElementsByTagName("location");
                    if (locs.getLength() > 0) {
                        return ((Element) locs.item(0)).getAttribute("href");
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[RepoIndexingService] repomd.xml 파싱 실패. path={}, type={}, err={}",
                    repomd, type, e.getMessage());
            return null;
        }
    }

    /** 사용자에게 표시할 repo 라벨 (BaseOS, AppStream 등). 하위 디렉토리명을 그대로 사용. */
    private String deriveRepoLabel(String rootBase, String repoBase) {
        try {
            Path rootPath = Paths.get(rootBase).toAbsolutePath().normalize();
            Path repoPath = Paths.get(repoBase).toAbsolutePath().normalize();
            if (repoPath.equals(rootPath)) return "root";
            Path relative = rootPath.relativize(repoPath);
            return relative.toString();
        } catch (Exception e) {
            return "repo";
        }
    }

    // =========================================================================
    // primary.xml.gz 파싱 — 패키지 이름 수집 (StAX streaming)
    // =========================================================================

    private void parsePrimary(String primaryPath, String repoLabel, OSMetadata meta,
                              Map<String, OSPackageRef> out) {
        try (InputStream in = openMaybeGzip(primaryPath)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(in);

            boolean inPackage = false;
            boolean expectName = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("package".equals(local) && reader.getAttributeCount() > 0) {
                        // primary.xml 의 package 요소는 type 속성을 가진다.
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            if ("type".equals(reader.getAttributeLocalName(i))
                                    && "rpm".equals(reader.getAttributeValue(i))) {
                                inPackage = true;
                                break;
                            }
                        }
                    } else if (inPackage && "name".equals(local)) {
                        expectName = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && expectName) {
                    String name = reader.getText().trim();
                    if (!name.isEmpty() && !out.containsKey(name)) {
                        out.put(name, OSPackageRef.builder()
                                .osMetadata(meta)
                                .name(name)
                                .repo(repoLabel)
                                .build());
                    }
                    expectName = false;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("package".equals(reader.getLocalName())) {
                        inPackage = false;
                    }
                }
            }
            reader.close();
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException(
                    "primary.xml 파싱 실패: " + primaryPath + " (" + e.getMessage() + ")", e);
        }
    }

    // =========================================================================
    // filelists.xml.gz 파싱 — .service unit 이름 수집 (StAX streaming)
    // =========================================================================

    private void parseFilelists(String filelistsPath, OSMetadata meta,
                                Map<String, OSServiceRef> out) {
        try (InputStream in = openMaybeGzip(filelistsPath)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(in);

            String currentPackage = null;
            StringBuilder fileBuf = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("package".equals(local)) {
                        currentPackage = reader.getAttributeValue(null, "name");
                    } else if ("file".equals(local)) {
                        fileBuf = new StringBuilder();
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && fileBuf != null) {
                    fileBuf.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("file".equals(local) && fileBuf != null) {
                        extractServiceName(fileBuf.toString().trim(), currentPackage, meta, out);
                        fileBuf = null;
                    } else if ("package".equals(local)) {
                        currentPackage = null;
                    }
                }
            }
            reader.close();
        } catch (IOException | XMLStreamException e) {
            // filelists 파싱 실패는 fatal 로 처리하지 않고 경고만 — 패키지 인덱스는 이미 저장됨.
            log.warn("[RepoIndexingService] filelists.xml 파싱 실패. path={}, err={}",
                    filelistsPath, e.getMessage());
        }
    }

    /**
     * 파일 경로에서 systemd unit 이름을 추출한다. systemd 경로 패턴 일치 + {@code .service} 확장자.
     *
     * <p>대상 경로:
     * <ul>
     *     <li>{@code /usr/lib/systemd/system/*.service}</li>
     *     <li>{@code /lib/systemd/system/*.service}</li>
     *     <li>{@code /etc/systemd/system/*.service}</li>
     * </ul>
     * 그 외 경로의 {@code .service} 확장자 파일은 systemd unit 이 아닐 수 있으므로 제외한다.</p>
     */
    private void extractServiceName(String filePath, String pkgName, OSMetadata meta,
                                    Map<String, OSServiceRef> out) {
        if (filePath == null || !filePath.endsWith(".service")) return;

        boolean systemdPath = filePath.contains("/systemd/system/");
        if (!systemdPath) return;

        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == filePath.length() - 1) return;

        String basename = filePath.substring(lastSlash + 1);
        // .service 제거
        String name = basename.substring(0, basename.length() - ".service".length());
        // @-instance unit (예: getty@.service) 은 인덱스에 포함하지 않음 (사용자가 @로 붙여 호출)
        if (name.isEmpty() || name.endsWith("@")) return;

        if (!out.containsKey(name)) {
            out.put(name, OSServiceRef.builder()
                    .osMetadata(meta)
                    .name(name)
                    .providedByPkg(pkgName)
                    .build());
        }
    }

    // =========================================================================
    // 공통 유틸
    // =========================================================================

    /** .gz 인지 여부에 따라 적절한 입력 스트림을 반환한다. */
    private InputStream openMaybeGzip(String path) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(Paths.get(path)));
        if (path.toLowerCase().endsWith(".gz")) {
            return new GZIPInputStream(raw);
        }
        return raw;
    }

    private Document parseXmlDom(InputStream input) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (Exception e) {
            throw new IllegalStateException("XML 파싱 실패: " + e.getMessage(), e);
        }
    }
}
