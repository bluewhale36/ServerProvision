package com.example.serverprovision.management.os.service.extractor;

import com.example.serverprovision.management.os.enums.OSName;
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

/**
 * RHEL 계열 (Rocky Linux, CentOS) 의 {@code repomd.xml → comps.xml(.gz)} 탐색·파싱 구현.
 * 로컬 디렉토리는 재귀 탐색으로 {@code repodata} 를 찾고, HTTP URL 은 알려진 경로 후보를 순차 시도한다.
 * 동일 환경·그룹 코드가 여러 repo 에 등장하면 먼저 발견된 정의를 우선한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RHELCompsExtractor implements CompsExtractorStrategy {

	private static final List<OSName> SUPPORTED = List.of(OSName.ROCKY_LINUX, OSName.CENTOS);

	/**
	 * 로컬 재귀 탐색 시 무시할 하위 디렉토리 — 패키지/이미지/부트 자료는 repodata 를 포함하지 않는다.
	 */
	private static final Set<String> LOCAL_SKIP_DIRS = Set.of(
			"Packages", "images", "isolinux", "EFI", "LiveOS", "postinstall"
	);

	/**
	 * HTTP 는 디렉토리 목록 조회가 불가능하므로 RHEL/Rocky/AlmaLinux 의 관용적 배치를 고정 후보로 둔다.
	 */
	private static final List<String> HTTP_REPODATA_CANDIDATES = List.of(
			"",                  // CentOS/RHEL 7 — ISO 루트
			"/AppStream",        // RHEL/Rocky/Alma 8+
			"/BaseOS",
			"/PowerTools",       // CentOS 8 Stream
			"/CRB",              // Rocky 9 / Alma 9
			"/extras",
			"/HighAvailability",
			"/Server"
	);

	private final RestClient restClient;

	@Override
	public boolean supports(OSName osName) {
		return SUPPORTED.contains(osName);
	}

	@Override
	public CompsExtractionResult extract(String preparedPath) {
		String basePath = preparedPath.endsWith("/")
				? preparedPath.substring(0, preparedPath.length() - 1)
				: preparedPath;

		log.info("[RHELCompsExtractor] 추출 시작. base={}", basePath);

		List<String> effectiveBases = findAllEffectiveBases(basePath);
		if (effectiveBases.isEmpty()) {
			boolean isHttp = basePath.startsWith("http://") || basePath.startsWith("https://");
			String detail = isHttp
					? "시도 경로: " + HTTP_REPODATA_CANDIDATES
					: "탐색 루트: " + basePath + " (최대 깊이 3), 제외: " + LOCAL_SKIP_DIRS;
			throw new IllegalStateException(
					"comps 데이터를 포함한 repomd.xml 을 찾을 수 없습니다. base=" + basePath + " | " + detail);
		}

		Map<String, CompsExtractionResult.GroupData> mergedGroups = new LinkedHashMap<>();
		List<CompsExtractionResult.EnvironmentData> mergedEnvironments = new ArrayList<>();
		Set<String> seenEnvCodes = new LinkedHashSet<>();

		for (String effectiveBase : effectiveBases) {
			log.info("[RHELCompsExtractor] repo 파싱. repoBase={}", effectiveBase);
			CompsExtractionResult partial = extractFromBase(effectiveBase);

			partial.allGroups().forEach(mergedGroups::putIfAbsent);

			for (CompsExtractionResult.EnvironmentData env : partial.environments()) {
				if (seenEnvCodes.add(env.environmentCode())) {
					mergedEnvironments.add(env);
				}
			}
		}

		log.info("[RHELCompsExtractor] 병합 완료. 환경={}, 그룹={}", mergedEnvironments.size(), mergedGroups.size());
		return new CompsExtractionResult(mergedEnvironments, mergedGroups);
	}

	// ---- 후보 경로 탐색 -------------------------------------------------

	private List<String> findAllEffectiveBases(String basePath) {
		if (basePath.startsWith("http://") || basePath.startsWith("https://")) {
			return findBasesHttp(basePath);
		}
		return findBasesLocal(basePath);
	}

	private List<String> findBasesLocal(String basePath) {
		Path root = Paths.get(basePath);
		if (!Files.isDirectory(root)) {
			// IsoPreparationService 가 사전 체크로 디렉토리 여부를 이미 보장하므로 여기 도달 시 내부 오류.
			throw new IllegalStateException(
					"내부 오류: 준비된 경로가 디렉토리가 아닙니다. base=" + basePath);
		}

		try (Stream<Path> stream = Files.walk(root, 3)) {
			List<String> result = stream
					.filter(Files::isDirectory)
					.filter(p -> p.getFileName() != null && "repodata".equals(p.getFileName().toString()))
					.filter(p -> !shouldSkip(p))
					.map(Path::getParent)
					.filter(Objects::nonNull)
					.map(p -> p.toAbsolutePath().toString())
					.filter(this::hasGroupData)
					.distinct()
					.collect(Collectors.toList());

			result.forEach(p -> log.info("[RHELCompsExtractor] 로컬 repo 발견. {}/repodata/repomd.xml", p));
			return result;
		} catch (IOException e) {
			throw new IllegalStateException("로컬 ISO 디렉토리 탐색 실패: " + basePath, e);
		}
	}

	private List<String> findBasesHttp(String basePath) {
		List<String> result = new ArrayList<>();
		for (String suffix : HTTP_REPODATA_CANDIDATES) {
			String candidate = basePath + suffix;
			if (hasGroupData(candidate)) {
				result.add(candidate);
				log.info("[RHELCompsExtractor] HTTP repo 발견. {}/repodata/repomd.xml", candidate);
			}
		}
		return result;
	}

	private boolean shouldSkip(Path repoDataPath) {
		for (Path component : repoDataPath) {
			if (LOCAL_SKIP_DIRS.contains(component.toString())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasGroupData(String base) {
		try {
			return fetchText(base + "/repodata/repomd.xml").contains("type=\"group\"");
		} catch (Exception e) {
			return false;
		}
	}

	// ---- comps 파싱 ----------------------------------------------------

	private CompsExtractionResult extractFromBase(String effectiveBase) {
		String repomdContent = fetchText(effectiveBase + "/repodata/repomd.xml");
		String compsHref = parseCompsHref(repomdContent);

		String compsUrl = compsHref.startsWith("http") ? compsHref : effectiveBase + "/" + compsHref;
		byte[] compsBytes = fetchBytes(compsUrl);

		InputStream compsStream = compsHref.endsWith(".gz")
				? decompress(compsBytes)
				: new ByteArrayInputStream(compsBytes);

		Document doc = parseXml(compsStream);
		Map<String, CompsExtractionResult.GroupData> groups = parseGroups(doc);
		List<CompsExtractionResult.EnvironmentData> environments = parseEnvironments(doc);

		return new CompsExtractionResult(environments, groups);
	}

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
		throw new IllegalStateException("repomd.xml 에서 comps 위치를 찾지 못했습니다.");
	}

	private Map<String, CompsExtractionResult.GroupData> parseGroups(Document doc) {
		Map<String, CompsExtractionResult.GroupData> result = new LinkedHashMap<>();
		NodeList groupNodes = doc.getElementsByTagName("group");
		for (int i = 0; i < groupNodes.getLength(); i++) {
			Element group = (Element) groupNodes.item(i);
			String id = textContent(group, "id");
			if (id == null || id.isBlank()) continue;

			result.put(
					id, new CompsExtractionResult.GroupData(
							id,
							defaultLangName(group),
							textContent(group, "description"),
							"true".equals(textContent(group, "default"))
					)
			);
		}
		return result;
	}

	private List<CompsExtractionResult.EnvironmentData> parseEnvironments(Document doc) {
		List<CompsExtractionResult.EnvironmentData> result = new ArrayList<>();
		NodeList envNodes = doc.getElementsByTagName("environment");
		for (int i = 0; i < envNodes.getLength(); i++) {
			Element env = (Element) envNodes.item(i);
			String id = textContent(env, "id");
			if (id == null || id.isBlank()) continue;

			List<String> groupCodes = new ArrayList<>();
			collectGroupIds(env, "grouplist", groupCodes);
			collectGroupIds(env, "optionlist", groupCodes);

			result.add(new CompsExtractionResult.EnvironmentData(
					"^" + id,  // Kickstart @^ 접두어
					defaultLangName(env),
					textContent(env, "description"),
					"true".equals(textContent(env, "default")),
					groupCodes
			));
		}
		return result;
	}

	private void collectGroupIds(Element parent, String containerTag, List<String> target) {
		NodeList containers = parent.getElementsByTagName(containerTag);
		if (containers.getLength() == 0) return;
		Element container = (Element) containers.item(0);
		NodeList groupIds = container.getElementsByTagName("groupid");
		for (int i = 0; i < groupIds.getLength(); i++) {
			String code = groupIds.item(i).getTextContent().trim();
			if (!code.isBlank() && !target.contains(code)) {
				target.add(code);
			}
		}
	}

	private String defaultLangName(Element parent) {
		NodeList names = parent.getElementsByTagName("name");
		for (int i = 0; i < names.getLength(); i++) {
			Element nameEl = (Element) names.item(i);
			String lang = nameEl.getAttribute("xml:lang");
			if (lang == null || lang.isBlank()) {
				return nameEl.getTextContent().trim();
			}
		}
		return "";
	}

	private String textContent(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes.getLength() == 0) return null;
		return nodes.item(0).getTextContent().trim();
	}

	private Document parseXml(InputStream input) {
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

	private InputStream decompress(byte[] compressed) {
		try {
			return new GZIPInputStream(new ByteArrayInputStream(compressed));
		} catch (Exception e) {
			throw new IllegalStateException("GZip 압축 해제 실패: " + e.getMessage(), e);
		}
	}

	private String fetchText(String path) {
		return new String(fetchBytes(path));
	}

	private byte[] fetchBytes(String path) {
		if (path.startsWith("http://") || path.startsWith("https://")) {
			return restClient.get().uri(path).retrieve().body(byte[].class);
		}
		try {
			return Files.readAllBytes(Paths.get(path));
		} catch (Exception e) {
			throw new IllegalStateException("로컬 파일 읽기 실패: " + path, e);
		}
	}
}
