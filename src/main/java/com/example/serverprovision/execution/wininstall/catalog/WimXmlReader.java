package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * WIM XML 자원(UTF-16LE · BOM) → {@link WindowsImage} 목록. 외부 파일이므로 DOCTYPE · 외부 엔티티를 차단한 파서로 읽는다.
 */
public final class WimXmlReader {

    private WimXmlReader() {
    }

    public static List<WindowsImage> read(byte[] utf16le) {
        String text = new String(utf16le, StandardCharsets.UTF_16LE);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Document document = parse(text);
        NodeList imageNodes = document.getDocumentElement().getElementsByTagName("IMAGE");
        List<WindowsImage> images = new ArrayList<>();
        for (int i = 0; i < imageNodes.getLength(); i++) {
            images.add(toImage((Element) imageNodes.item(i)));
        }
        return List.copyOf(images);
    }

    private static Document parse(String text) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new WimFormatException("WIM XML 을 해석할 수 없습니다: " + e.getMessage());
        }
    }

    private static WindowsImage toImage(Element image) {
        String indexText = image.getAttribute("INDEX");
        int index;
        try {
            index = Integer.parseInt(indexText);
        } catch (NumberFormatException e) {
            throw new WimFormatException("IMAGE 의 INDEX 가 정수가 아닙니다: " + indexText);
        }
        String name = childText(image, "NAME");
        if (name == null || name.isBlank()) {
            throw new WimFormatException("IMAGE " + index + " 에 NAME 이 없습니다.");
        }
        Element windows = child(image, "WINDOWS");
        String editionId = windows == null ? "" : orEmpty(childText(windows, "EDITIONID"));
        String installationType = windows == null ? "" : orEmpty(childText(windows, "INSTALLATIONTYPE"));
        String language = "";
        String build = "";
        if (windows != null) {
            Element languages = child(windows, "LANGUAGES");
            language = languages == null ? "" : orEmpty(childText(languages, "DEFAULT"));
            Element version = child(windows, "VERSION");
            if (version != null) {
                build = String.join(".",
                        orEmpty(childText(version, "MAJOR")), orEmpty(childText(version, "MINOR")),
                        orEmpty(childText(version, "BUILD")), orEmpty(childText(version, "SPBUILD")));
            }
        }
        String displayName = childText(image, "DISPLAYNAME");
        return new WindowsImage(index, new WindowsImageName(name),
                displayName == null || displayName.isBlank() ? name.trim() : displayName.trim(),
                editionId, installationType, language, build);
    }

    /** 직계 자식 요소 하나 — 같은 태그가 손자에도 있을 수 있어(NAME 등) 직계만 본다. */
    private static Element child(Element parent, String tag) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && tag.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String childText(Element parent, String tag) {
        Element element = child(parent, tag);
        return element == null ? null : element.getTextContent();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
