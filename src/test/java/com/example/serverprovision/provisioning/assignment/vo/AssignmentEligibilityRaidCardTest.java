package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind.AssignmentBlock;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 할당 판정의 RAID 카드 축(R20) — plan v2 §12 진리표 7 행과 판정 순서.
 *
 * <p>집행과 같은 {@code RaidCardMatch.judge} 를 쓰되, 카드를 지정했고 Subsystem 도 있는데 인벤토리만 없으면 judge 를
 * 부르지 않고 "대조 전" 으로 둔다 — 할당 시점의 미수집은 "아직 모름" 이라 막을 근거가 아니다.</p>
 */
class AssignmentEligibilityRaidCardTest {

    private static final RequiredRaidCard MEGARAID =
            new RequiredRaidCard(2L, "1000:9361", "MegaRAID 9361-8i", true);
    private static final RequiredRaidCard NO_SUBSYSTEM =
            new RequiredRaidCard(3L, null, "구형 카드", true);
    private static final RequiredRaidCard PURGED = new RequiredRaidCard(7L, null, null, false);

    private static final RaidInventory MEGARAID_SERVER = inventory("1000:9361", "AVAGO MegaRAID SAS 9361-8i");
    private static final RaidInventory SAS3008_SERVER = inventory("1458:3008", "SAS3008");
    private static final RaidInventory NO_CARD_SERVER = new RaidInventory(null, List.of(), List.of());

    private static RaidInventory inventory(String subsystem, String model) {
        return new RaidInventory(new DetectedRaidCard(null, subsystem, model, "1.0"), List.of(), List.of());
    }

    private static GuestServer server(boolean decommissioned) {
        GuestServer server = GuestServer.builder().build();
        if (decommissioned) {
            server.decommission(LocalDateTime.now());
        }
        return server;
    }

    private static GuestServerDetail detailWithBoard(Long boardId, String boardName) {
        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(boardId);
        given(board.getModelName()).willReturn(boardName);
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBoardModel()).willReturn(board);
        return detail;
    }

    private static AssignmentEligibility card(RequiredRaidCard required, RaidInventory inventory) {
        return new AssignmentEligibility(server(false), detailWithBoard(3L, "MS74-HB0"), null, null, required, inventory);
    }

    @Test
    @DisplayName("RAID 구성 단계 없음 — 인벤토리가 없어도 통과 · 대조 전 아님(NOT_APPLICABLE)")
    void noRaidStep_passesWithoutMarker() {
        AssignmentEligibility context = card(null, null);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.NOT_APPLICABLE);
        assertThat(context.raidCardBlockReason()).isNull();
        assertThat(context.unverifiedReason()).isNull();
    }

    @Test
    @DisplayName("카드 미지정 — 인벤토리 미수집이어도 NOT_APPLICABLE(미수집 분기는 카드를 지정했을 때만)")
    void unspecifiedCard_isNotApplicableEvenUncollected() {
        AssignmentEligibility context = card(RequiredRaidCard.unspecified(), null);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.NOT_APPLICABLE);
        assertThat(context.unverifiedReason()).isNull();
    }

    @Test
    @DisplayName("카드에 Subsystem 미등록 — 막지 않고 대조 전(사유: Subsystem 미등록), 미수집이어도 이 사유가 먼저")
    void unregisteredSubsystem_unverifiable() {
        AssignmentEligibility context = card(NO_SUBSYSTEM, null);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.UNVERIFIABLE);
        assertThat(context.raidCardBlockReason()).isNull();
        assertThat(context.unverifiedReason())
                .isEqualTo("RAID 카드 구형 카드 에 PCI Subsystem 이 등록되지 않아 이 서버의 카드와 대조할 수 없습니다.");
    }

    @Test
    @DisplayName("카드 자원 영구 삭제 — 막지 않고 대조 전(사유: 카드 없음)")
    void purgedCard_unverifiableWithOwnReason() {
        AssignmentEligibility context = card(PURGED, SAS3008_SERVER);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.UNVERIFIABLE);
        assertThat(context.unverifiedReason())
                .isEqualTo("정의서가 지정한 RAID 카드(#7)를 찾을 수 없어 이 서버의 카드와 대조할 수 없습니다.");
    }

    @Test
    @DisplayName("인벤토리 미수집 — judge 를 부르지 않고 대조 전(사유: RAID 카드 미확인)")
    void uncollected_unverifiedNotBlocked() {
        AssignmentEligibility context = card(MEGARAID, null);

        assertThat(context.raidCardVerdict()).isNull();
        assertThat(context.raidCardBlockReason()).isNull();
        assertThat(context.unverifiedReason())
                .isEqualTo("이 정의서는 RAID 카드 MegaRAID 9361-8i 전용인데 이 서버의 RAID 카드는 아직 확인되지 않았습니다.");
    }

    @Test
    @DisplayName("수집했는데 카드 없음 — 잠김(NOT_DETECTED), 집행의 CARD_NOT_DETECTED 와 같은 판정")
    void collectedWithoutCard_blocks() {
        AssignmentEligibility context = card(MEGARAID, NO_CARD_SERVER);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.NOT_DETECTED);
        assertThat(context.raidCardBlockReason())
                .isEqualTo("본 게스트 서버에서 RAID 카드가 감지되지 않아 정의서의 RAID 카드(MegaRAID 9361-8i, 1000:9361)와 호환되지 않습니다.");
    }

    @Test
    @DisplayName("다른 카드 — 잠김(MISMATCH), 지정 · 감지 카드를 함께 알린다")
    void differentCard_blocks() {
        AssignmentEligibility context = card(MEGARAID, SAS3008_SERVER);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.MISMATCH);
        assertThat(context.raidCardBlockReason())
                .isEqualTo("본 게스트 서버의 RAID 카드(SAS3008, 1458:3008)와 정의서의 RAID 카드(MegaRAID 9361-8i, 1000:9361)가 호환되지 않습니다.");
        AssignmentBlock block = AssignmentBlockKind.evaluate(context);
        assertThat(block.kind()).isEqualTo(AssignmentBlockKind.RAID_CARD_MISMATCH);
        assertThat(block.hardwareIncompatible()).isTrue();
    }

    @Test
    @DisplayName("같은 카드 — 통과(MATCH) · 대조 전 아님")
    void sameCard_passes() {
        AssignmentEligibility context = card(MEGARAID, MEGARAID_SERVER);

        assertThat(context.raidCardVerdict()).isEqualTo(RaidCardMatchVerdict.MATCH);
        assertThat(AssignmentBlockKind.evaluate(context)).isNull();
        assertThat(context.unverifiedReason()).isNull();
    }

    @Test
    @DisplayName("판정 순서 — 보드도 카드도 다르면 보드 사유가 먼저(상수 선언 순서)")
    void boardMismatchWinsOverCard() {
        AssignmentEligibility context = new AssignmentEligibility(server(false), detailWithBoard(3L, "MS74-HB0"),
                new RequiredBoardModel(4L, "MS04-CE0"), null, MEGARAID, SAS3008_SERVER);

        assertThat(AssignmentBlockKind.evaluate(context).kind()).isEqualTo(AssignmentBlockKind.BOARD_MISMATCH);
    }

    @Test
    @DisplayName("대조 전 사유가 둘 — 보드 미수집과 카드 미확인을 잇는다")
    void bothAxesUnverified_joinsReasons() {
        AssignmentEligibility context = new AssignmentEligibility(server(false), null,
                new RequiredBoardModel(4L, "MS04-CE0"), null, MEGARAID, null);

        assertThat(context.unverifiedReason()).isEqualTo(
                "이 정의서는 특정 메인보드를 요구하는데 이 서버의 하드웨어는 아직 수집 전입니다. "
                        + "이 정의서는 RAID 카드 MegaRAID 9361-8i 전용인데 이 서버의 RAID 카드는 아직 확인되지 않았습니다.");
    }

    @Test
    @DisplayName("하드웨어 축만 — 회수 · BIOS 템플릿은 빼고 카드 불일치는 잡는다(이미 든 할당의 경고)")
    void evaluateHardware_skipsNonHardwareKinds() {
        AssignmentEligibility decommissionedMatch = new AssignmentEligibility(server(true),
                detailWithBoard(3L, "MS74-HB0"), null, "BIOS 템플릿이 어긋납니다", MEGARAID, MEGARAID_SERVER);
        AssignmentEligibility decommissionedMismatch = new AssignmentEligibility(server(true),
                detailWithBoard(3L, "MS74-HB0"), null, "BIOS 템플릿이 어긋납니다", MEGARAID, SAS3008_SERVER);

        assertThat(AssignmentBlockKind.evaluateHardware(decommissionedMatch)).isNull();
        assertThat(AssignmentBlockKind.evaluateHardware(decommissionedMismatch).kind())
                .isEqualTo(AssignmentBlockKind.RAID_CARD_MISMATCH);
        assertThat(AssignmentBlockKind.evaluate(decommissionedMismatch).kind())
                .isEqualTo(AssignmentBlockKind.DECOMMISSIONED);
    }
}
