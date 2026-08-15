package com.example.serverprovision.provisioning.group.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.ReferencedDefinitionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 그룹 표준 정의서의 네 가지 상태 (U3-5-d).
 *
 * <p>여기서 못 박는 것은 <b>"정해 두었는가" 와 "지금 쓸 수 있는가" 가 다른 질문</b>이라는 것이다.
 * 표준을 정해 두었는데 그 정의서가 비활성이 되거나 아예 사라진 상태가 실제로 존재하며, 그때 화면은
 * 여전히 표준 절을 그려 <b>해제 · 변경 버튼을 내야</b> 한다. 둘을 한 값으로 뭉개면 그 순간 사용자가
 * 잘못된 표준에서 빠져나갈 방법을 잃는다.</p>
 */
class GroupStandardResponseTest {

    private static SettingSummaryResponse summary(long id, String name, boolean deprecated) {
        return new SettingSummaryResponse(id, name, List.of(SettingProcessType.OS_INSTALLATION),
                false, true, deprecated, LocalDateTime.now(), null, null);
    }

    @Test
    @DisplayName("정하지 않은 그룹 — 정해 두었는가도 쓸 수 있는가도 거짓이고 이름이 없다")
    void noneIsAValidState() {
        GroupStandardResponse standard = GroupStandardResponse.none();

        assertThat(standard.present()).isFalse();
        assertThat(standard.usable()).isFalse();
        assertThat(standard.definitionId()).isNull();
        assertThat(standard.name()).isNull();
        assertThat(standard.blockReason()).isNull();
        assertThat(standard.definition()).isNull();
    }

    @Test
    @DisplayName("정상 — 정해 두었고 쓸 수 있으며, 배너 계산에 넘길 요약이 함께 온다")
    void usableStandardCarriesItsSummary() {
        GroupStandardResponse standard = GroupStandardResponse.of(
                new ReferencedDefinitionResponse(1L, summary(1L, "web-standard", false), null), List.of());

        assertThat(standard.present()).isTrue();
        assertThat(standard.usable()).isTrue();
        assertThat(standard.name()).isEqualTo("web-standard");
        assertThat(standard.deprecated()).isFalse();
        // 배너는 이 요약으로 미리보기를 돌린다 — 없으면 요구 보드를 대조할 수 없다
        assertThat(standard.definition()).isNotNull();
        assertThat(standard.definition().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용 중단 권고는 차단이 아니다 — 표준으로 두는 것도 적용도 막지 않고 표시만 한다")
    void deprecatedStandardIsStillUsable() {
        GroupStandardResponse standard = GroupStandardResponse.of(
                new ReferencedDefinitionResponse(1L, summary(1L, "old-standard", true), null), List.of());

        assertThat(standard.deprecated()).isTrue();
        assertThat(standard.usable()).isTrue();
    }

    @Test
    @DisplayName("비활성 — 정해 두었지만 쓸 수 없고, 사유가 그대로 화면에 실린다")
    void disabledStandardIsPresentButNotUsable() {
        String reason = "비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)";
        GroupStandardResponse standard = GroupStandardResponse.of(
                new ReferencedDefinitionResponse(1L, summary(1L, "web-standard", false), reason), List.of());

        // 정해 두었다는 사실은 살아 있어야 한다 — 그래야 화면이 [바꾸기] · [해제] 를 낸다
        assertThat(standard.present()).isTrue();
        assertThat(standard.usable()).isFalse();
        // 사유는 도메인 SSOT 가 만든 것 그대로다 — 표준 지정을 거절하는 서버 가드와 같은 문자열
        assertThat(standard.blockReason()).isEqualTo(reason);
        assertThat(standard.name()).isEqualTo("web-standard");
    }

    @Test
    @DisplayName("정의서가 아예 사라짐 — 소프트참조라 정상 상태이고, 이름 자리에 id 가 남는다")
    void goneStandardKeepsItsIdentity() {
        GroupStandardResponse standard = GroupStandardResponse.of(ReferencedDefinitionResponse.gone(9L), List.of());

        assertThat(standard.present()).isTrue();      // 그룹은 여전히 표준을 '정해 둔' 상태다
        assertThat(standard.usable()).isFalse();
        assertThat(standard.definitionId()).isEqualTo(9L);
        // 빈칸으로 두면 사용자가 무엇을 해제하려는지 알 수 없다
        assertThat(standard.name()).contains("9");
        assertThat(standard.blockReason()).isNotBlank();
        // 사라진 정의서에 사용 중단 권고를 물을 대상이 없다
        assertThat(standard.deprecated()).isFalse();
    }
}
