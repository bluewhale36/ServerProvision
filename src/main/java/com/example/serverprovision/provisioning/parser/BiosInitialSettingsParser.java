package com.example.serverprovision.provisioning.parser;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.exception.BiosResourceLoadException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code initial_settings.md} (Redfish {@code GET .../Bios} 응답을 기록한 마크다운) → AttributeName → 현재값 맵.
 * 마크다운의 ```json``` 펜스 블록을 추출해 top-level {@code Attributes} 를 파싱한다.
 * 값은 문자열/숫자 혼합이므로 모두 문자열로 정규화한다 (화면 표시 + diff 기준값 용도).
 */
@Component
public class BiosInitialSettingsParser {

	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	public Map<BiosAttributeName, String> parse(InputStream in) {
		String markdown;
		try {
			markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new BiosResourceLoadException("초기 설정 파일 읽기 실패", e);
		}
		RawInitial raw = MAPPER.readValue(extractJsonBlock(markdown), RawInitial.class);
		Map<BiosAttributeName, String> result = new LinkedHashMap<>();
		if (raw.attributes() != null) {
			raw.attributes().forEach((name, value) -> {
				if (name != null && !name.isBlank() && value != null) {
					result.put(BiosAttributeName.of(name), String.valueOf(value));
				}
			});
		}
		return result;
	}

	// 마크다운에서 ```json ... ``` 블록 본문을 추출. 펜스가 없으면 첫 '{' ~ 마지막 '}' fallback.
	private static String extractJsonBlock(String markdown) {
		int fence = markdown.indexOf("```json");
		if (fence < 0) {
			int start = markdown.indexOf('{');
			int end = markdown.lastIndexOf('}');
			if (start >= 0 && end > start) {
				return markdown.substring(start, end + 1);
			}
			throw new BiosResourceLoadException("초기 설정 JSON 블록을 찾지 못했습니다.");
		}
		int bodyStart = markdown.indexOf('\n', fence);
		int bodyEnd = markdown.indexOf("```", bodyStart + 1);
		if (bodyStart < 0 || bodyEnd < 0) {
			throw new BiosResourceLoadException("초기 설정 JSON 블록이 닫히지 않았습니다.");
		}
		return markdown.substring(bodyStart + 1, bodyEnd);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawInitial(@JsonProperty("Attributes") Map<String, Object> attributes) {
	}
}
