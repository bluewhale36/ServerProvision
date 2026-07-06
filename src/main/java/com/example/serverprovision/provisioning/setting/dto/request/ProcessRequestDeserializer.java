package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.Map;

/**
 * {@link AbstractProcessRequest} 의 2단 판별자({@code type} → {@code osFamily}) 해석기.
 *
 * <p><b>왜 {@code @JsonTypeInfo} 가 아닌가</b> : Jackson 3 은 외부 판별자가 고른 subtype 이
 * 스스로 또 다형(내부 {@code @JsonTypeInfo})인 2단 중첩을 체이닝하지 않는다 — 추상 중간 타입을
 * 직접 생성하려다 실패한다(U2-1 CP4 에서 최소 재현으로 확정). wire 계약(flat JSON + type/osFamily
 * 판별자, plan v2 D10)은 그대로 두고 해석만 이 컴포넌트가 맡는다.
 * {@code @JacksonComponent} 라서 애플리케이션·{@code @WebMvcTest} 슬라이스 양쪽에 자동 등록된다.</p>
 *
 * <p><b>판별자 등록 지점 (SSOT)</b> : 새 단계 타입·OS 계열 추가 시 아래 맵에 한 항목을 더한다
 * (구 {@code @JsonSubTypes} 한 줄과 같은 확장 비용). 예약된 {@code WINDOWS} 계열은
 * {@code WindowsInstallationRequest} 가 실체화될 때 {@code OS_SUBTYPES} 에 등록한다 —
 * 등록 전 전송은 아래 가드가 400 으로 거절한다.</p>
 */
@JacksonComponent
public class ProcessRequestDeserializer extends ValueDeserializer<AbstractProcessRequest> {

    private static final Map<SettingProcessType, Class<? extends AbstractProcessRequest>> FLAT_SUBTYPES = Map.of(
            SettingProcessType.BASIC_UPDATE, BasicUpdateRequest.class,
            SettingProcessType.BASIC_SETTING, BasicSettingRequest.class);

    private static final Map<SettingProcessType, Map<OSFamily, Class<? extends AbstractProcessRequest>>> OS_SUBTYPES = Map.of(
            SettingProcessType.OS_INSTALLATION, Map.of(
                    OSFamily.RHEL_BASED, RHELInstallationRequest.class,
                    OSFamily.DEBIAN_BASED, UbuntuInstallationRequest.class),
            SettingProcessType.OS_SETTING, Map.of(
                    OSFamily.RHEL_BASED, RHELOSSettingRequest.class));

    @Override
    public AbstractProcessRequest deserialize(JsonParser parser, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(parser);
        SettingProcessType type = readDiscriminator(ctxt, node, "type", SettingProcessType.class);

        Class<? extends AbstractProcessRequest> target = FLAT_SUBTYPES.get(type);
        if (target == null) {
            target = resolveOSSubtype(ctxt, node, type);
        }
        // concrete 타입에 남아 있는 type/osFamily 키는 read-only accessor 와 매칭되어 무시된다.
        return ctxt.readTreeAsValue(node, target);
    }

    private Class<? extends AbstractProcessRequest> resolveOSSubtype(
            DeserializationContext ctxt, JsonNode node, SettingProcessType type) throws JacksonException {
        Map<OSFamily, Class<? extends AbstractProcessRequest>> familyMap = OS_SUBTYPES.get(type);
        if (familyMap == null) {
            // enum 상수는 늘었는데 등록이 누락된 경우 — silent 500 대신 명시적 입력 불일치로 드러낸다.
            return ctxt.reportInputMismatch(AbstractProcessRequest.class,
                    "단계 타입 '%s' 의 하위 타입 등록이 없습니다.", type);
        }
        OSFamily family = readDiscriminator(ctxt, node, "osFamily", OSFamily.class);
        Class<? extends AbstractProcessRequest> target = familyMap.get(family);
        if (target == null) {
            return ctxt.reportInputMismatch(AbstractProcessRequest.class,
                    "단계 타입 '%s' 이 지원하지 않는 osFamily 입니다: %s", type, family);
        }
        return target;
    }

    private <E extends Enum<E>> E readDiscriminator(
            DeserializationContext ctxt, JsonNode node, String property, Class<E> enumType) throws JacksonException {
        JsonNode value = node.get(property);
        if (value == null || !value.isString()) {
            return ctxt.reportInputMismatch(AbstractProcessRequest.class,
                    "프로비저닝 단계에 판별자 '%s' 가 없습니다.", property);
        }
        try {
            return Enum.valueOf(enumType, value.asString());
        } catch (IllegalArgumentException e) {
            return ctxt.reportInputMismatch(AbstractProcessRequest.class,
                    "알 수 없는 판별자 값입니다: %s=%s", property, value.asString());
        }
    }
}
