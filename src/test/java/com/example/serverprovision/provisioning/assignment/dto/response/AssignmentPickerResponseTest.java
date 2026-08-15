package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U3-5-b — 정의서 선택 모달 한 판의 조립({@link AssignmentPickerResponse#of}).
 *
 * <p>여기서 하는 일은 조회가 아니라 <b>맞추기</b>다. 할당 폼(어느 것이 잠겼는가)과 정의서 상세(무엇을
 * 하는 정의서인가)는 각자의 서비스가 이미 만들어 오고, 이 팩터리는 id 로 짝을 지어 좌측 한 줄과 우측 한
 * 판을 한 항목으로 묶는다. 그래서 서비스가 아니라 응답에 있고, 트랜잭션 없이 이렇게 검증할 수 있다.</p>
 */
class AssignmentPickerResponseTest {

    private static final String BLOCK_REASON =
            "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 ASUS-Z13PE 입니다.";

    private SettingSummaryResponse summary(long id, String name) {
        return new SettingSummaryResponse(id, name,
                List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.now());
    }

    private SettingDetailResponse detail(long id, String name) {
        return new SettingDetailResponse(id, name, false, true, false, 0L,
                List.of(), List.of(), List.of(), ReferenceNamesResponse.empty(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("선택지마다 제 상세가 붙는다 — 잠긴 것도 상세를 갖는다 (DEC-C)")
    void of_pairsEveryOptionWithItsOwnDetail() {
        AssignmentFormResponse form = new AssignmentFormResponse(null, List.of(
                new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, false),
                new DefinitionOptionResponse(summary(2L, "bios-ms03"), BLOCK_REASON, false),
                new DefinitionOptionResponse(summary(3L, "fw-asus-z13"), null, true)));

        AssignmentPickerResponse picker = AssignmentPickerResponse.of(form, List.of(
                // 상세가 선택지와 다른 순서로 와도 id 로 맞춘다 — 두 서비스의 정렬이 같다고 가정하지 않는다
                detail(3L, "fw-asus-z13"), detail(1L, "os-only-auto"), detail(2L, "bios-ms03")));

        assertThat(picker.items()).hasSize(3);
        assertThat(picker.items()).allSatisfy(item ->
                assertThat(item.detail().id()).isEqualTo(item.id()));
        // 좌측 순서는 선택지 순서를 따른다(상세가 온 순서가 아니라) — 목록 정렬은 선택지 쪽 계약이다
        assertThat(picker.items()).extracting(DefinitionPickerItemResponse::name)
                .containsExactly("os-only-auto", "bios-ms03", "fw-asus-z13");

        // 잠긴 항목도 우측 상세를 갖는다 — 사유만 보여주면 다음 판단을 못 한다
        DefinitionPickerItemResponse blocked = picker.items().get(1);
        assertThat(blocked.blocked()).isTrue();
        assertThat(blocked.blockReason()).isEqualTo(BLOCK_REASON);
        assertThat(blocked.detail()).isNotNull();

        // 대조 전 표식은 차단과 다른 축이다 — 막지 않고 표식만 단다
        assertThat(picker.items().get(2).blocked()).isFalse();
        assertThat(picker.items().get(2).unverified()).isTrue();
    }

    @Test
    @DisplayName("상세가 오지 않은 선택지는 떨군다 — 두 재료를 읽는 사이에 삭제된 것이다")
    void of_dropsOptionWhoseDetailIsMissing() {
        AssignmentFormResponse form = new AssignmentFormResponse(null, List.of(
                new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, false),
                new DefinitionOptionResponse(summary(2L, "bios-ms03"), null, false)));

        AssignmentPickerResponse picker =
                AssignmentPickerResponse.of(form, List.of(detail(1L, "os-only-auto")));

        assertThat(picker.items()).extracting(DefinitionPickerItemResponse::id).containsExactly(1L);
    }

    @Test
    @DisplayName("전부 잠겼으면 고를 수 있는 것이 없다 — 목록은 그대로 남는다")
    void hasSelectable_falseWhenEveryOptionBlocked() {
        AssignmentFormResponse form = new AssignmentFormResponse(null, List.of(
                new DefinitionOptionResponse(summary(1L, "bios-ms03"), BLOCK_REASON, false),
                new DefinitionOptionResponse(summary(2L, "bios-ms03-b"), BLOCK_REASON, false)));

        AssignmentPickerResponse picker = AssignmentPickerResponse.of(form,
                List.of(detail(1L, "bios-ms03"), detail(2L, "bios-ms03-b")));

        assertThat(picker.hasSelectable()).isFalse();
        // 고를 수 없다고 목록에서 지우지 않는다 — 사라지면 "없어진 것" 과 "안 맞는 것" 을 구분할 수 없다
        assertThat(picker.items()).hasSize(2);
    }

    @Test
    @DisplayName("선택지가 없으면 빈 판 — 상세만 있어도 항목이 생기지 않는다")
    void of_emptyWhenNoOptions() {
        AssignmentPickerResponse picker = AssignmentPickerResponse.of(
                new AssignmentFormResponse(null, List.of()), List.of(detail(1L, "os-only-auto")));

        assertThat(picker.items()).isEmpty();
        assertThat(picker.hasSelectable()).isFalse();
    }
}
