package com.example.serverprovision.provisioning.group.service;

import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupMemberRepository;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupRepository;
import com.example.serverprovision.provisioning.setting.dto.response.ReferencedDefinitionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DefinitionNotAssignableException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 표준 세팅 정의서 지정 · 해제의 가드 (U3-5-d).
 *
 * <p>정상 흐름에서는 모달이 붙일 수 없는 정의서를 애초에 목록에 내지 않는다. 그래서 여기서 재현하는
 * 것은 <b>direct POST</b> 와 <b>고르는 사이에 관리자가 비활성화한 경합</b>이다.</p>
 *
 * <p>거절해야 하는 이유는 사용성이다 — 붙일 수 없는 정의서를 표준으로 두면 그룹 상세의 안내 배너가
 * 영원히 잠긴 채 남는다. 판정은 할당 경로와 같은 도메인 SSOT
 * ({@code SettingDefinition.assignBlockReason()})를 쓰므로 두 경로가 같은 말로 거절한다.</p>
 *
 * <p><b>이 서비스가 할당 리포지토리를 아예 주입받지 않는다</b>는 것도 여기서 드러난다 — 표준을 바꿔도
 * 기존 할당이 흔들릴 수 없는 것이 규율이 아니라 구조다. 검증할 호출이 없다는 사실 자체가 보장이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class GuestServerGroupCommandServiceStandardTest {

    @Mock GuestServerGroupRepository groupRepository;
    @Mock GuestServerGroupMemberRepository memberRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock SettingQueryService settingQueryService;

    @InjectMocks GuestServerGroupCommandService service;

    private static final long GROUP = 7L;
    private static final long DEFINITION = 11L;

    private static SettingSummaryResponse summary(String name) {
        return new SettingSummaryResponse(DEFINITION, name, List.of(SettingProcessType.OS_INSTALLATION),
                false, true, false, LocalDateTime.now(), null, null);
    }

    private GuestServerGroup givenGroup() {
        GuestServerGroup group = GuestServerGroup.create("8월 A동 1차");
        given(groupRepository.findById(GROUP)).willReturn(Optional.of(group));
        return group;
    }

    // ==== 지정 =======================================================

    @Test
    @DisplayName("지정 — 컬럼에 id 가 남고 이름을 돌려준다(호출자가 flash 에 싣는다)")
    void setStandardStoresIdAndReturnsName() {
        GuestServerGroup group = givenGroup();
        given(settingQueryService.resolveReference(DEFINITION)).willReturn(
                new ReferencedDefinitionResponse(DEFINITION, summary("web-standard"), null));

        String name = service.setStandardDefinition(GROUP, DEFINITION);

        assertThat(name).isEqualTo("web-standard");
        assertThat(group.getStandardDefinitionId()).isEqualTo(DEFINITION);
    }

    @Test
    @DisplayName("멤버가 없는 그룹에도 지정된다 — 미리 만들어 두고 정책부터 정하는 것이 출발점이다 (R3)")
    void emptyGroupCanHaveStandard() {
        GuestServerGroup group = givenGroup();
        given(settingQueryService.resolveReference(DEFINITION)).willReturn(
                new ReferencedDefinitionResponse(DEFINITION, summary("web-standard"), null));

        service.setStandardDefinition(GROUP, DEFINITION);

        assertThat(group.memberCount()).isZero();
        assertThat(group.hasStandard()).isTrue();
    }

    // ==== 거절 =======================================================

    @Test
    @DisplayName("없는 정의서 — 404 로 끊고 컬럼을 건드리지 않는다")
    void missingDefinitionIsRejected() {
        GuestServerGroup group = givenGroup();
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(ReferencedDefinitionResponse.gone(DEFINITION));

        assertThatThrownBy(() -> service.setStandardDefinition(GROUP, DEFINITION))
                .isInstanceOf(SettingNotFoundException.class);

        assertThat(group.hasStandard()).isFalse();
    }

    @Test
    @DisplayName("비활성 정의서 — 409 로 거절하고 사유는 도메인이 만든 문자열 그대로다")
    void disabledDefinitionIsRejectedWithDomainReason() {
        GuestServerGroup group = givenGroup();
        String reason = "비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)";
        given(settingQueryService.resolveReference(DEFINITION)).willReturn(
                new ReferencedDefinitionResponse(DEFINITION, summary("web-standard"), reason));

        assertThatThrownBy(() -> service.setStandardDefinition(GROUP, DEFINITION))
                .isInstanceOf(DefinitionNotAssignableException.class)
                // 화면 tooltip · 할당 거절 · 표준 지정 거절이 한 문자열에서 나온다
                .hasMessageContaining(reason);

        assertThat(group.hasStandard()).isFalse();
    }

    @Test
    @DisplayName("없는 그룹 — 정의서를 해석하기도 전에 404 다. 없는 그룹에 무엇을 물을지가 없다")
    void missingGroupIsRejectedBeforeResolvingDefinition() {
        given(groupRepository.findById(GROUP)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStandardDefinition(GROUP, DEFINITION))
                .isInstanceOf(GuestServerGroupNotFoundException.class);

        verifyNoInteractions(settingQueryService);
    }

    // ==== 해제 =======================================================

    @Test
    @DisplayName("해제 — 컬럼만 비우고 정의서를 조회하지 않는다(해제에 판정할 것이 없다)")
    void clearStandardNeedsNoDefinitionLookup() {
        GuestServerGroup group = givenGroup();
        group.assignStandard(DEFINITION);

        service.clearStandardDefinition(GROUP);

        assertThat(group.hasStandard()).isFalse();
        verifyNoInteractions(settingQueryService);
    }

    @Test
    @DisplayName("표준이 없는 그룹을 해제해도 오류가 아니다 — 멱등")
    void clearingAbsentStandardIsIdempotent() {
        GuestServerGroup group = givenGroup();

        service.clearStandardDefinition(GROUP);

        assertThat(group.getStandardDefinitionId()).isNull();
    }
}
