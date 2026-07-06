package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

/**
 * U2-3-1 CP4 — BASIC_UPDATE 검사기 단위 (U2-3 서비스 단위에서 이동: D6 가드 + deprecated 서술).
 */
@ExtendWith(MockitoExtension.class)
class BasicUpdateReferenceInspectorTest {

    private static final com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext CTX =
            new com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext(java.util.List.of());

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @InjectMocks BasicUpdateReferenceInspector inspector;

    private static BasicUpdateRequest specified(Long boardId, Long biosId) {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, boardId),
                biosId == null ? new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null)
                        : new FirmwareSelectionRequest(FirmwareSelectionMode.SPECIFIED, biosId),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    private static BasicUpdateRequest auto() {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    @Test
    @DisplayName("AUTO — id 참조가 없어 가드·서술 모두 불발")
    void auto_skipsAll() {
        assertThatCode(() -> inspector.validateReferences(auto(), CTX)).doesNotThrowAnyException();
        assertThat(inspector.describeDeprecatedReferences(auto())).isEmpty();
    }

    @Test
    @DisplayName("보드 부존재 404 · disabled 409(field=boardModel)")
    void board_notFoundAndDisabled() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(specified(99L, null), CTX))
                .isInstanceOf(BoardModelNotFoundException.class);

        BoardModel disabled = Mockito.mock(BoardModel.class);
        given(disabled.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabled.getModelName()).thenReturn("MS73-HB1");
        given(boardModelRepository.findByIdAndIsDeletedFalse(6L)).willReturn(Optional.of(disabled));
        assertThatThrownBy(() -> inspector.validateReferences(specified(6L, null), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .hasFieldOrPropertyWithValue("fieldName", "boardModel");
    }

    @Test
    @DisplayName("펌웨어 — 타 보드 소속/부존재 forging 404 · disabled 409(field=bios)")
    void firmware_forgingAndDisabled() {
        BoardModel board = Mockito.mock(BoardModel.class);
        given(board.isEnabled()).willReturn(true);
        given(boardModelRepository.findByIdAndIsDeletedFalse(6L)).willReturn(Optional.of(board));

        given(biosRepository.findByIdAndBoardModel_Id(3L, 6L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(specified(6L, 3L), CTX))
                .isInstanceOf(BiosNotFoundException.class);

        BoardBIOS disabledBios = Mockito.mock(BoardBIOS.class);
        given(disabledBios.isDeleted()).willReturn(false);
        given(disabledBios.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabledBios.getVersion()).thenReturn("F08");
        given(biosRepository.findByIdAndBoardModel_Id(4L, 6L)).willReturn(Optional.of(disabledBios));
        assertThatThrownBy(() -> inspector.validateReferences(specified(6L, 4L), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .hasFieldOrPropertyWithValue("fieldName", "bios");
    }

    @Test
    @DisplayName("deprecated 서술 — 보드/펌웨어의 deprecated 만 표시명으로 수집(거절 아님)")
    void describeDeprecated_collectsNames() {
        BoardModel deprecatedBoard = Mockito.mock(BoardModel.class);
        given(deprecatedBoard.isDeprecated()).willReturn(true);
        given(deprecatedBoard.getModelName()).willReturn("MD72-HB3");
        given(boardModelRepository.findByIdAndIsDeletedFalse(8L)).willReturn(Optional.of(deprecatedBoard));
        BoardBIOS bios = Mockito.mock(BoardBIOS.class);
        given(bios.isDeleted()).willReturn(false);
        given(bios.isDeprecated()).willReturn(false);
        given(biosRepository.findById(3L)).willReturn(Optional.of(bios));

        assertThat(inspector.describeDeprecatedReferences(specified(8L, 3L)))
                .containsExactly("메인보드 MD72-HB3");
    }
}
