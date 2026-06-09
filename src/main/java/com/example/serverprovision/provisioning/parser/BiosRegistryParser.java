package com.example.serverprovision.provisioning.parser;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosDependency;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.enums.RedfishMapToProperty;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import com.example.serverprovision.provisioning.domain.vo.PasswordLength;
import com.example.serverprovision.provisioning.exception.BiosResourceLoadException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redfish {@code BiosAttributeRegistry} JSON 파서.
 * <p>Jackson 3 tree API(asText→asString 등 rename)를 피하기 위해 {@code readValue} + {@code @JsonProperty}
 * 로 타입화 역직렬화한다. 타입별 필드 추출은 외부 데이터 → 도메인 매핑 경계이므로 switch 가 정당하다
 * (행위가 자라는 도메인 분기가 아니라 1회성 변환).</p>
 */
@Component
public class BiosRegistryParser {

	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	/** 파싱 결과: 속성 맵(문서 순서 보존) + 의존성. */
	public record ParsedRegistry(
			Map<BiosAttributeName, BiosAttribute> attributes,
			List<BiosDependency> dependencies
	) {
	}

	public ParsedRegistry parse(InputStream in) {
		RawRegistry raw = MAPPER.readValue(in, RawRegistry.class);
		if (raw.registryEntries() == null || raw.registryEntries().attributes() == null) {
			throw new BiosResourceLoadException("레지스트리에 RegistryEntries.Attributes 가 없습니다.");
		}
		Map<BiosAttributeName, BiosAttribute> attrs = new LinkedHashMap<>();
		for (RawAttribute ra : raw.registryEntries().attributes()) {
			BiosAttribute a = toAttribute(ra);
			attrs.put(a.name(), a);
		}
		List<BiosDependency> deps = new ArrayList<>();
		if (raw.registryEntries().dependencies() != null) {
			for (RawDependency rd : raw.registryEntries().dependencies()) {
				toDependency(rd, attrs).ifPresent(deps::add);
			}
		}
		return new ParsedRegistry(attrs, deps);
	}

	private BiosAttribute toAttribute(RawAttribute raw) {
		BiosAttributeType type = BiosAttributeType.from(raw.type());
		BiosAttributeName name = BiosAttributeName.of(raw.attributeName());
		List<BiosEnumOption> options = List.of();
		IntegerBounds bounds = null;
		PasswordLength passwordLength = null;
		String defaultValue = raw.defaultValue() == null ? null : String.valueOf(raw.defaultValue());

		switch (type) {
			case ENUMERATION -> options = raw.value() == null ? List.of()
					: raw.value().stream()
					.map(o -> new BiosEnumOption(o.valueName(), o.valueDisplayName()))
					.toList();
			case INTEGER -> bounds = new IntegerBounds(
					clampToLong(raw.lowerBound()), clampToLong(raw.upperBound()), clampToLong(raw.scalarIncrement()));
			case PASSWORD -> {
				passwordLength = new PasswordLength(
						raw.minLength() == null ? 0 : raw.minLength(),
						raw.maxLength() == null ? Integer.MAX_VALUE : raw.maxLength());
				defaultValue = null; // 비밀번호는 기본값 노출/사용 안 함
			}
			case BOOLEAN -> {
				// true/false 만 — 추가 메타(options/bounds/passwordLength) 없음.
				// defaultValue 는 JSON boolean → String.valueOf 로 이미 "true"/"false".
			}
		}

		return new BiosAttribute(name, type, raw.displayName(), raw.helpText(), raw.menuPath(),
				raw.readOnly(), raw.resetRequired(), defaultValue, options, bounds, passwordLength);
	}

	private Optional<BiosDependency> toDependency(RawDependency raw, Map<BiosAttributeName, BiosAttribute> attrs) {
		if (raw.dependency() == null) {
			return Optional.empty();
		}
		RawDependencyBody body = raw.dependency();
		if (body.mapFrom() == null || body.mapFrom().isEmpty()) {
			return Optional.empty();
		}
		RawMapFrom first = body.mapFrom().get(0);
		if (first.mapFromAttribute() == null || body.mapToAttribute() == null) {
			return Optional.empty();
		}
		BiosAttributeName from = BiosAttributeName.of(first.mapFromAttribute());
		BiosAttributeName to = BiosAttributeName.of(body.mapToAttribute());
		// dangling 참조(레지스트리에 없는 from/to) 는 조용히 skip — NPE 방지.
		if (!attrs.containsKey(from) || !attrs.containsKey(to)) {
			return Optional.empty();
		}
		RedfishMapToProperty prop = RedfishMapToProperty.from(body.mapToProperty());
		if (prop == RedfishMapToProperty.UNKNOWN) {
			return Optional.empty();
		}
		String fromVal = first.mapFromValue() == null ? "" : String.valueOf(first.mapFromValue());
		return Optional.of(new BiosDependency(from, fromVal, to, prop));
	}

	// 일부 레지스트리는 Integer bound 에 long 범위를 넘는 sentinel(예: 2^64-1)을 넣는다. BigInteger 로 받아 long 으로 saturate.
	private static long clampToLong(BigInteger v) {
		if (v == null) {
			return 0;
		}
		if (v.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
			return Long.MAX_VALUE;
		}
		if (v.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
			return Long.MIN_VALUE;
		}
		return v.longValue();
	}

	/* ─────────────────────────── raw JSON 매핑 DTO ─────────────────────────── */

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawRegistry(@JsonProperty("RegistryEntries") RawEntries registryEntries) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawEntries(
			@JsonProperty("Attributes") List<RawAttribute> attributes,
			@JsonProperty("Dependencies") List<RawDependency> dependencies
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawAttribute(
			@JsonProperty("AttributeName") String attributeName,
			@JsonProperty("Type") String type,
			@JsonProperty("DisplayName") String displayName,
			@JsonProperty("HelpText") String helpText,
			@JsonProperty("MenuPath") String menuPath,
			@JsonProperty("ReadOnly") boolean readOnly,
			@JsonProperty("ResetRequired") boolean resetRequired,
			@JsonProperty("DefaultValue") Object defaultValue,
			@JsonProperty("Value") List<RawOption> value,
			@JsonProperty("LowerBound") BigInteger lowerBound,
			@JsonProperty("UpperBound") BigInteger upperBound,
			@JsonProperty("ScalarIncrement") BigInteger scalarIncrement,
			@JsonProperty("MinLength") Integer minLength,
			@JsonProperty("MaxLength") Integer maxLength
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawOption(
			@JsonProperty("ValueName") String valueName,
			@JsonProperty("ValueDisplayName") String valueDisplayName
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawDependency(@JsonProperty("Dependency") RawDependencyBody dependency) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawDependencyBody(
			@JsonProperty("MapFrom") List<RawMapFrom> mapFrom,
			@JsonProperty("MapToAttribute") String mapToAttribute,
			@JsonProperty("MapToProperty") String mapToProperty,
			@JsonProperty("MapToValue") Object mapToValue
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RawMapFrom(
			@JsonProperty("MapFromAttribute") String mapFromAttribute,
			@JsonProperty("MapFromCondition") String mapFromCondition,
			@JsonProperty("MapFromValue") Object mapFromValue
	) {
	}
}
