package com.example.serverprovision.provisioning.parser;

import com.example.serverprovision.provisioning.domain.BiosAttributeControl;
import com.example.serverprovision.provisioning.domain.BiosControl;
import com.example.serverprovision.provisioning.domain.BiosPage;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.BiosSubmenuControl;
import com.example.serverprovision.provisioning.domain.enums.BiosComplexHint;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.PageId;
import com.example.serverprovision.provisioning.exception.BiosResourceLoadException;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser.ParsedRegistry;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AMI {@code SetupData} XML 파서. UTF-16 LE(BOM) 바이트를 DOM 이 BOM 자동 인식으로 디코딩한다 —
 * XML 선언의 {@code encoding="UTF-8"} 은 거짓이므로 절대 신뢰하지 않고 raw InputStream 을 그대로 넘긴다.
 *
 * <p>핵심: 4개 {@code <Platform>} 블록은 한 메뉴를 나눠 담은 것이므로 <b>전부 병합</b>한다(전역 PageID 유일).
 * leaf/submenu 판별자는 {@code Type="Submenu"} 속성의 유무. submenu 의 navigable 은
 * (dest 실존 && dest≠자기페이지) 로 dead link / self-ref 액션을 한 번에 흡수한다.</p>
 */
@Component
public class BiosSetupDataParser {

	public BiosSetupMenu parse(InputStream xml, ParsedRegistry registry,
	                           Map<BiosAttributeName, String> currentValues, String boardKey) {
		Document doc = parseSecure(xml);
		Element root = doc.getDocumentElement();
		String rootName = localOrNodeName(root);
		if (!"SetupData".equals(rootName)) {
			throw new BiosResourceLoadException("SetupData 루트 요소가 아닙니다: " + rootName);
		}

		// Pass 1 — 모든 Platform 의 모든 <Page> 를 문서 순서로 수집(전역 맵), 메뉴바 도출.
		NodeList pageNodes = root.getElementsByTagName("Page");
		Map<PageId, RawPage> rawPages = new LinkedHashMap<>();
		List<PageId> menuBarOrder = new ArrayList<>();
		for (int i = 0; i < pageNodes.getLength(); i++) {
			Element pageEl = (Element) pageNodes.item(i);
			PageId id = PageId.of(required(pageEl.getAttribute("PageID"), "PageID"));
			if (rawPages.containsKey(id)) {
				throw new BiosResourceLoadException("중복 PageID 감지: " + id);
			}
			String parentRaw = pageEl.getAttribute("PageParentID");
			PageId parent = PageId.of(parentRaw == null || parentRaw.isBlank() ? "0x0" : parentRaw);
			RawPage rp = new RawPage(id, parent, pageEl.getAttribute("PageTitle"),
					pageEl.getAttribute("PageFlags"), readControls(pageEl));
			rawPages.put(id, rp);
			if (parent.isRoot()) {
				menuBarOrder.add(id);
			}
		}

		// AMI anchor/content 분리 보정: 메뉴 dest 가 빈 'anchor' 페이지이고 실제 위젯이 동일
		// (PageParentID, PageTitle) 형제 'content' 페이지에 있는 경우(예: Trusted Computing 0x4 ← 0x5+0x6),
		// 형제 컨트롤을 anchor 로 병합해 클릭 시 빈 화면이 뜨지 않게 한다. 병합 대상이 없으면 그대로 빈 페이지로 둔다
		// (조건부 장치 부재로 실제 BIOS 도 비워 보여주는 경우 — 정상 fidelity).
		Map<String, List<RawControl>> contentByGroup = new LinkedHashMap<>();
		for (RawPage rp : rawPages.values()) {
			if (!rp.controls().isEmpty()) {
				contentByGroup.computeIfAbsent(siblingGroupKey(rp), k -> new ArrayList<>()).addAll(rp.controls());
			}
		}

		// Pass 2 — navigable 해석(전체 PageId 집합 필요) 후 도메인 페이지 빌드.
		Map<PageId, BiosPage> pages = new LinkedHashMap<>();
		for (RawPage rp : rawPages.values()) {
			List<RawControl> effective = rp.controls();
			if (effective.isEmpty()) {
				List<RawControl> mergedSiblings = contentByGroup.get(siblingGroupKey(rp));
				if (mergedSiblings != null) {
					effective = mergedSiblings;
				}
			}
			List<BiosControl> controls = new ArrayList<>(effective.size());
			for (RawControl rc : effective) {
				if (rc.submenu()) {
					boolean navigable = rc.dest() != null
							&& rawPages.containsKey(rc.dest())
							&& !rc.dest().equals(rp.id());
					controls.add(new BiosSubmenuControl(
							rc.dest() != null ? rc.dest() : PageId.ROOT,
							rc.label(), rc.help(), rc.refGuid(), rc.complex(), navigable));
				} else {
					controls.add(new BiosAttributeControl(BiosAttributeName.of(rc.attributeName()), rc.complex()));
				}
			}
			pages.put(rp.id(), new BiosPage(rp.id(), rp.parent(), rp.title(), rp.flags(), List.copyOf(controls)));
		}

		return new BiosSetupMenu(boardKey, List.copyOf(menuBarOrder),
				Collections.unmodifiableMap(pages), registry.attributes(), registry.dependencies(),
				currentValues == null ? Map.of() : currentValues);
	}

	private List<RawControl> readControls(Element pageEl) {
		List<RawControl> result = new ArrayList<>();
		NodeList children = pageEl.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node n = children.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element el = (Element) n;
			if (!"Control".equals(localOrNodeName(el))) {
				continue;
			}
			BiosComplexHint complex = BiosComplexHint.fromXml(el.getAttribute("Complex"));
			if ("Submenu".equals(el.getAttribute("Type"))) {
				String destRaw = el.getAttribute("ControlDestPageID");
				PageId dest = (destRaw == null || destRaw.isBlank()) ? null : PageId.of(destRaw);
				result.add(RawControl.submenu(dest, el.getAttribute("DisplayName"),
						el.getAttribute("HelpText"), el.getAttribute("RefGuid"), complex));
			} else {
				String attrName = el.getAttribute("AttributeName");
				if (attrName == null || attrName.isBlank()) {
					continue; // Type 도 없고 AttributeName 도 없는 컨트롤은 의미 없음 — skip.
				}
				result.add(RawControl.leaf(attrName, complex));
			}
		}
		return result;
	}

	private Document parseSecure(InputStream in) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			// XXE 방어 — 외부 엔티티 / DOCTYPE 차단.
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
			dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			dbf.setXIncludeAware(false);
			dbf.setExpandEntityReferences(false);
			DocumentBuilder db = dbf.newDocumentBuilder();
			// SetupData 는 UTF-16 LE(BOM FF FE) 인데 XML 선언이 encoding="UTF-8" 로 '거짓말' 을 한다.
			// 바이트 스트림을 그대로 넘기면 Xerces 가 선언의 거짓 인코딩으로 본문을 재디코딩해
			// "프롤로그에 콘텐츠 불가" 로 깨진다. → BOM 으로 charset 을 직접 판별해 디코딩한 character Reader 를
			// 넘기면 파서가 선언 인코딩을 무시하고 Reader 의 문자를 그대로 사용한다 (거짓 선언 무력화).
			InputSource source = new InputSource(decodedReader(in.readAllBytes()));
			return db.parse(source);
		} catch (Exception e) {
			throw new BiosResourceLoadException("SetupData XML 파싱 실패", e);
		}
	}

	/** BOM 으로 charset 을 판별하고 BOM 을 제거한 character Reader 를 만든다 (UTF-16 LE/BE · UTF-8). */
	private static Reader decodedReader(byte[] bytes) {
		if (hasPrefix(bytes, 0xFF, 0xFE)) {
			return slice(bytes, 2, StandardCharsets.UTF_16LE);
		}
		if (hasPrefix(bytes, 0xFE, 0xFF)) {
			return slice(bytes, 2, StandardCharsets.UTF_16BE);
		}
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
			return slice(bytes, 3, StandardCharsets.UTF_8);
		}
		return slice(bytes, 0, StandardCharsets.UTF_8);
	}

	private static boolean hasPrefix(byte[] bytes, int b0, int b1) {
		return bytes.length >= 2 && (bytes[0] & 0xFF) == b0 && (bytes[1] & 0xFF) == b1;
	}

	private static Reader slice(byte[] bytes, int offset, Charset charset) {
		return new InputStreamReader(new ByteArrayInputStream(bytes, offset, bytes.length - offset), charset);
	}

	private static String localOrNodeName(Element el) {
		return el.getLocalName() != null ? el.getLocalName() : el.getNodeName();
	}

	private static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new BiosResourceLoadException("필수 속성 누락: " + field);
		}
		return value;
	}

	// 동일 (PageParentID, PageTitle) 형제 그룹 키 — anchor/content 병합용.
	// parent hex 는 항상 "0x.." 형태(공백 없음)라 공백 구분자로 충돌이 발생하지 않는다.
	private static String siblingGroupKey(RawPage rp) {
		return rp.parent().hex() + ' ' + (rp.title() == null ? "" : rp.title());
	}

	/* ─────────────────────────── 파싱 임시 구조 ─────────────────────────── */

	private record RawPage(PageId id, PageId parent, String title, String flags, List<RawControl> controls) {
	}

	private record RawControl(
			boolean submenu, String attributeName, PageId dest,
			String label, String help, String refGuid, BiosComplexHint complex
	) {
		static RawControl leaf(String attributeName, BiosComplexHint complex) {
			return new RawControl(false, attributeName, null, null, null, null, complex);
		}

		static RawControl submenu(PageId dest, String label, String help, String refGuid, BiosComplexHint complex) {
			return new RawControl(true, null, dest, label, help, refGuid, complex);
		}
	}
}
