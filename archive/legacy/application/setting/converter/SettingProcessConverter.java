package com.example.serverprovision.application.setting.converter;

import com.example.serverprovision.application.setting.model.SettingProcess;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link SettingProcess}와 DB 컬럼 JSON 문자열 간 직렬화/역직렬화를 담당하는 JPA {@code AttributeConverter}이다.
 *
 * <p>역할: {@code ServerSetting}의 {@code process} 컬럼에 {@link SettingProcess}를
 * JSON 문자열로 저장하고, 조회 시 다시 {@link SettingProcess}로 복원한다.
 * {@code @Converter(autoApply = true)}로 선언되어 {@link SettingProcess} 타입 필드에
 * 자동 적용된다. Jackson 3 런타임({@code tools.jackson.databind.ObjectMapper})을 사용한다.</p>
 *
 * <p>유스케이스: JPA가 엔티티를 저장할 때 이 Converter의 {@link #convertToDatabaseColumn}을 호출하고,
 * 조회 시 {@link #convertToEntityAttribute}를 호출한다.
 * 역직렬화 시 {@link SettingProcess} 내부의 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}
 * 구현체는 JSON의 {@code "type"} 필드 판별자로 선택되며, 각 OS 설치 도메인 모델은
 * {@link com.example.serverprovision.domain.os.model.installation.OSInstallation}
 * 의 {@code "osType"} 판별자로 선택된다.</p>
 *
 * <p><b>레거시 호환 정책</b>: 이전 버전에서는 OS 설치 판별자로 {@code "ROCKY_LINUX"} 단일 값만
 * 사용했으나, 현재는 메이저 버전을 포함한 {@code ROCKY_LINUX_8}/{@code ROCKY_LINUX_9}/
 * {@code ROCKY_LINUX_10} 을 사용한다. 읽기 경로({@link #convertToEntityAttribute})에서
 * {@code osType == "ROCKY_LINUX"} 를 발견하면 동일 프로세스의 {@code osMetadata.osVersion}
 * 접두사({@code "8."}/{@code "9."}/{@code "10."})로 새 판별자를 유추하여 승격한다.
 * 버전 파싱에 실패하면 {@code ROCKY_LINUX_9} 로 기본 매핑하며 경고 로그를 남긴다.
 * 쓰기 경로({@link #convertToDatabaseColumn})에서는 이 fixup 이 필요하지 않다.</p>
 *
 * <p>확장 가이드: 새 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}
 * 구현체를 추가할 때 이 Converter 자체는 수정할 필요가 없다.
 * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@code @JsonSubTypes}에 새 타입을 등록하는 것으로 충분하다.
 * {@code ObjectMapper}는 Spring 컨텍스트에서 주입받으므로, 애플리케이션의 공통 Jackson 설정
 * (예: 날짜 포맷, 다형성 설정)이 이 Converter에도 자동으로 적용된다.</p>
 */
@Slf4j
@Converter(autoApply = true)
@RequiredArgsConstructor
public class SettingProcessConverter implements AttributeConverter<SettingProcess, String> {

    /**
     * 레거시 판별자 값 — 메이저 버전 정보가 없는 Rocky Linux 구버전 JSON 에서 사용됨.
     */
    private static final String LEGACY_ROCKY_DISCRIMINATOR = "ROCKY_LINUX";

    /**
     * 레거시 Rocky Linux JSON 이지만 {@code osMetadata.osVersion} 파싱에 실패했을 때
     * 사용되는 기본 승격 대상 판별자. 실서비스 기준 Rocky 9 채택 비율이 가장 높다.
     */
    private static final String DEFAULT_ROCKY_FALLBACK = "ROCKY_LINUX_9";

    private final ObjectMapper objectMapper;

    /**
     * {@link SettingProcess}를 DB 저장용 JSON 문자열로 직렬화한다.
     *
     * @param attribute 직렬화할 {@link SettingProcess}. {@code null}이면 {@code null}을 반환한다.
     * @return JSON 문자열. {@code attribute}가 {@code null}이면 {@code null}.
     * @throws IllegalArgumentException Jackson 직렬화 실패 시
     */
    @Override
    public String convertToDatabaseColumn(SettingProcess attribute) {
        if (attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize SettingProcess", e);
        }
    }

    /**
     * DB의 JSON 문자열을 {@link SettingProcess}로 역직렬화한다.
     *
     * <p>읽기 경로에서만 레거시 {@code "osType":"ROCKY_LINUX"} 를 메이저 버전별 신 판별자로
     * 승격하는 fixup 을 수행한다. 쓰기 경로에서는 이미 신 판별자로 직렬화되므로 추가 처리가 없다.</p>
     *
     * @param dbData DB에서 읽은 JSON 문자열. {@code null} 또는 빈 문자열이면 {@code null}을 반환한다.
     * @return 역직렬화된 {@link SettingProcess}. {@code dbData}가 {@code null}/빈 문자열이면 {@code null}.
     * @throws IllegalArgumentException Jackson 역직렬화 실패 시 (JSON 형식 오류 또는 알 수 없는 {@code type})
     */
    @Override
    public SettingProcess convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(dbData);
            migrateLegacyRockyDiscriminator(root);
            return objectMapper.treeToValue(root, SettingProcess.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize SettingProcess", e);
        }
    }

    /**
     * {@link SettingProcess} JSON 트리를 순회하여 레거시 Rocky Linux 판별자를 승격한다.
     *
     * <p>판별자 승격 규칙:</p>
     * <ul>
     *   <li>{@code osMetadata.osVersion} 이 {@code "8."} 으로 시작 → {@code ROCKY_LINUX_8}</li>
     *   <li>{@code osMetadata.osVersion} 이 {@code "9."} 으로 시작 → {@code ROCKY_LINUX_9}</li>
     *   <li>{@code osMetadata.osVersion} 이 {@code "10."} 으로 시작 → {@code ROCKY_LINUX_10}</li>
     *   <li>위 조건 모두 불일치 또는 {@code osVersion} 부재 → {@link #DEFAULT_ROCKY_FALLBACK}
     *       (경고 로그)</li>
     * </ul>
     *
     * @param root {@link SettingProcess} JSON 의 루트 노드
     */
    // visible-for-testing — 테스트에서 트리 레벨 fixup 동작을 단독 검증한다.
    void migrateLegacyRockyDiscriminator(JsonNode root) {
        JsonNode processList = root.get("processList");
        if (processList == null || !processList.isArray()) return;

        for (JsonNode process : processList) {
            if (!(process instanceof ObjectNode processNode)) continue;

            // OS_INSTALLATION 타입만 fixup 대상
            JsonNode typeNode = processNode.get("type");
            if (typeNode == null || !"OS_INSTALLATION".equals(typeNode.asString())) continue;

            JsonNode osInstallationNode = processNode.get("osInstallation");
            if (!(osInstallationNode instanceof ObjectNode osInstObjNode)) continue;

            JsonNode osTypeNode = osInstObjNode.get("osType");
            if (osTypeNode == null || !LEGACY_ROCKY_DISCRIMINATOR.equals(osTypeNode.asString())) continue;

            // osMetadata.osVersion 으로 메이저 버전 추출
            String newDiscriminator = resolveRockyDiscriminatorByVersion(processNode);
            osInstObjNode.put("osType", newDiscriminator);
            log.info("레거시 Rocky Linux 판별자 승격. '{}' → '{}'",
                    LEGACY_ROCKY_DISCRIMINATOR, newDiscriminator);
        }
    }

    /**
     * 프로세스 JSON 노드의 {@code osMetadata.osVersion} 으로 승격 대상 판별자를 결정한다.
     *
     * @param processNode {@code "type":"OS_INSTALLATION"} 프로세스 노드
     * @return {@code ROCKY_LINUX_8} / {@code ROCKY_LINUX_9} / {@code ROCKY_LINUX_10}
     *         또는 결정 불가 시 {@link #DEFAULT_ROCKY_FALLBACK}
     */
    private String resolveRockyDiscriminatorByVersion(ObjectNode processNode) {
        JsonNode osMetadataNode = processNode.get("osMetadata");
        if (osMetadataNode == null) {
            log.warn("레거시 Rocky Linux 판별자 감지 — osMetadata 부재로 '{}' 로 폴백",
                    DEFAULT_ROCKY_FALLBACK);
            return DEFAULT_ROCKY_FALLBACK;
        }

        JsonNode versionNode = osMetadataNode.get("osVersion");
        if (versionNode == null || versionNode.isNull()) {
            log.warn("레거시 Rocky Linux 판별자 감지 — osVersion 부재로 '{}' 로 폴백",
                    DEFAULT_ROCKY_FALLBACK);
            return DEFAULT_ROCKY_FALLBACK;
        }

        String osVersion = versionNode.asString();
        if (osVersion.startsWith("10.")) return "ROCKY_LINUX_10";
        if (osVersion.startsWith("9."))  return "ROCKY_LINUX_9";
        if (osVersion.startsWith("8."))  return "ROCKY_LINUX_8";

        log.warn("레거시 Rocky Linux 판별자 감지 — osVersion='{}' 접두사 불일치로 '{}' 로 폴백",
                osVersion, DEFAULT_ROCKY_FALLBACK);
        return DEFAULT_ROCKY_FALLBACK;
    }
}
