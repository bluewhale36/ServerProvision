package com.example.serverprovision.provisioning.setting.entity;

import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SettingDefinition} 도메인 메서드 단위 — 활성 · 사용 중단 권고 전이와 할당 차단 사유 SSOT(U3-2-b DEC-G).
 * 서버 가드({@code AssignmentCommandService})와 할당 드롭다운 필터가 함께 호출하는
 * {@code assignBlockReason()} 의 상태별 반환을 고정한다 — 두 곳이 갈라지면 이 테스트가 먼저 깨진다.
 */
class SettingDefinitionTest {

    private static final String DELETED_REASON = "삭제된 정의서는 할당할 수 없습니다";
    private static final String DISABLED_REASON = "비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)";

    private SettingDefinition definition() {
        return SettingDefinition.builder()
                .name("표준 세팅")
                .processes(List.of(new SettingProcess(new ProcessPayload(new BasicSettingRequest(List.of())))))
                .build();
    }

    @Test
    @DisplayName("신규 정의서는 활성 · 비deprecated 로 시작한다(생성 기본값)")
    void newDefinition_startsEnabled() {
        SettingDefinition definition = definition();

        assertThat(definition.isEnabled()).isTrue();
        assertThat(definition.isDeprecated()).isFalse();
        assertThat(definition.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("toggleEnabled — 반전(활성↔비활성). 부모가 없어 재계산할 effective 도 없다")
    void toggleEnabled_flips() {
        SettingDefinition definition = definition();

        definition.toggleEnabled();
        assertThat(definition.isEnabled()).isFalse();

        definition.toggleEnabled();
        assertThat(definition.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("deprecate / undeprecate — 멱등(같은 액션 반복해도 상태 불변 · 예외 없음)")
    void deprecate_undeprecate_idempotent() {
        SettingDefinition definition = definition();

        definition.deprecate();
        definition.deprecate();
        assertThat(definition.isDeprecated()).isTrue();

        definition.undeprecate();
        definition.undeprecate();
        assertThat(definition.isDeprecated()).isFalse();
    }

    @Test
    @DisplayName("deprecate 는 활성 축을 건드리지 않는다(deprecated ≠ disabled — 차원 독립)")
    void deprecate_doesNotDisable() {
        SettingDefinition definition = definition();

        definition.deprecate();

        assertThat(definition.isEnabled()).isTrue();
        assertThat(definition.assignBlockReason()).isNull();   // 권고일 뿐 차단 아님
    }

    @Test
    @DisplayName("assignBlockReason — 정상(활성) → null (할당 가능)")
    void assignBlockReason_active_null() {
        assertThat(definition().assignBlockReason()).isNull();
    }

    @Test
    @DisplayName("assignBlockReason — 비활성 → 활성화 유도 사유(신규 할당 차단)")
    void assignBlockReason_disabled_reason() {
        SettingDefinition definition = definition();
        definition.toggleEnabled();

        assertThat(definition.assignBlockReason()).isEqualTo(DISABLED_REASON);
    }

    @Test
    @DisplayName("assignBlockReason — 삭제 → 삭제 사유가 우선한다(비활성 여부와 무관)")
    void assignBlockReason_deleted_takesPrecedence() {
        SettingDefinition definition = definition();
        definition.toggleEnabled();   // 비활성 + 삭제 중첩 — 삭제가 먼저 보고된다
        definition.softDelete();

        assertThat(definition.assignBlockReason()).isEqualTo(DELETED_REASON);
    }

    @Test
    @DisplayName("assignBlockReason — 복원해도 비활성이면 여전히 차단(축이 독립적으로 유지됨)")
    void assignBlockReason_restoredButDisabled_stillBlocked() {
        SettingDefinition definition = definition();
        definition.toggleEnabled();
        definition.softDelete();
        definition.restore();

        assertThat(definition.assignBlockReason()).isEqualTo(DISABLED_REASON);
    }
}
