package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequiredBoardModel} 단위 — 요구 보드 추출과 대조 판정 (U3-5-a).
 *
 * <p>추출은 {@code AbstractProcessRequest.requiredBoardModel()} 다형 accessor 를 타므로 단계 타입을
 * {@code instanceof} 로 가르지 않는다. 그래서 여기 검증은 "어떤 단계 조합에서 무엇이 나오는가" 에 집중한다.</p>
 */
class RequiredBoardModelTest {

    private static final Function<Long, String> NAMES = id -> "MS03-CE0";

    private static BoardModelSelectionRequest auto() {
        return new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null);
    }

    private static BoardModelSelectionRequest specified(Long boardId) {
        return new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, boardId);
    }

    private static BasicUpdateRequest firmware(BoardModelSelectionRequest board) {
        FirmwareSelectionRequest latest = new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null);
        return new BasicUpdateRequest(board, latest, latest);
    }

    /**
     * 보드를 고르지 않는 단계 — 베이스의 기본 구현({@code null})을 그대로 쓴다.
     *
     * <p>실재 타입(OS 설치 등) 대신 최소 구현을 쓰는 이유는, 여기서 확인하려는 것이 <b>다형 accessor 의
     * 기본값</b>이지 특정 단계 타입의 필드가 아니기 때문이다. 보드를 고르지 않는 단계가 늘어도 이 검증은
     * 그대로 성립한다.</p>
     */
    private static AbstractProcessRequest boardUnaware() {
        return new AbstractProcessRequest() {
            @Override
            public SettingProcessType processType() {
                return SettingProcessType.OS_INSTALLATION;
            }
        };
    }

    // ─────────────────────────── 추출 ───────────────────────────

    @Test
    @DisplayName("추출 — 보드를 고르는 단계가 없으면 요구 보드도 없다")
    void from_noBoardAwareProcess_returnsNull() {
        List<AbstractProcessRequest> processes = List.of(boardUnaware());

        assertThat(RequiredBoardModel.from(processes, NAMES)).isNull();
    }

    @Test
    @DisplayName("추출 — 전부 AUTO 면 요구하는 보드가 없다(실행 시점 감지)")
    void from_allAuto_returnsNull() {
        List<AbstractProcessRequest> processes = List.of(firmware(auto()), new BasicSettingRequest(List.of(7L)));

        assertThat(RequiredBoardModel.from(processes, NAMES)).isNull();
    }

    @Test
    @DisplayName("추출 — SPECIFIED 면 그 보드를 요구한다")
    void from_specified_returnsBoard() {
        List<AbstractProcessRequest> processes = List.of(firmware(specified(3L)));

        RequiredBoardModel required = RequiredBoardModel.from(processes, NAMES);

        assertThat(required).isNotNull();
        assertThat(required.id()).isEqualTo(3L);
        assertThat(required.name()).isEqualTo("MS03-CE0");
    }

    @Test
    @DisplayName("추출 — 펌웨어와 BIOS 설정이 같은 보드를 지정하면 하나로 모인다")
    void from_bothProcessesSpecifySameBoard_returnsOne() {
        List<AbstractProcessRequest> processes = List.of(
                firmware(specified(3L)),
                new BasicSettingRequest(specified(3L), List.of(7L)));

        assertThat(RequiredBoardModel.from(processes, NAMES).id()).isEqualTo(3L);
    }

    @Test
    @DisplayName("추출 — 목록이 null 이면 요구 보드도 없다(방어)")
    void from_nullProcesses_returnsNull() {
        assertThat(RequiredBoardModel.from(null, NAMES)).isNull();
    }

    // ─────────────────────────── 대조 ───────────────────────────

    @Test
    @DisplayName("대조 — 서버 보드가 같으면 통과")
    void blockReasonFor_sameBoard_passes() {
        RequiredBoardModel required = new RequiredBoardModel(3L, "MS03-CE0");

        assertThat(required.blockReasonFor(3L, "MS03-CE0")).isNull();
    }

    @Test
    @DisplayName("대조 — 서버 보드가 다르면 요구 보드와 실제 보드를 함께 알린다")
    void blockReasonFor_differentBoard_explainsBoth() {
        RequiredBoardModel required = new RequiredBoardModel(3L, "MS03-CE0");

        String reason = required.blockReasonFor(9L, "X11SPM");

        assertThat(reason).contains("MS03-CE0").contains("X11SPM");
    }

    @Test
    @DisplayName("대조 — 서버 보드를 아직 모르면 막지 않는다(수집 전 서버에 미리 할당하는 흐름 보호)")
    void blockReasonFor_unknownServerBoard_passes() {
        RequiredBoardModel required = new RequiredBoardModel(3L, "MS03-CE0");

        assertThat(required.blockReasonFor(null, null)).isNull();
    }
}
