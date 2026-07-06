package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.exception.InvalidBiosTemplateSelectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U2-2-3 CP4 — BASIC_SETTING 검사기: 자체 보드 selector(2026-07-07 개정) 기준의
 * 보드 가드 + 템플릿 실존/보드 중복/보드 일치 규칙.
 */
@ExtendWith(MockitoExtension.class)
class BasicSettingReferenceInspectorTest {

    private static final ProcessValidationContext CTX = new ProcessValidationContext(List.of());

    @Mock BiosSettingTemplateRepository repository;
    @Mock BoardModelRepository boardModelRepository;
    @InjectMocks BasicSettingReferenceInspector inspector;

    private BiosSettingTemplate template(Long boardId, String boardName) {
        BiosSettingTemplate template = Mockito.mock(BiosSettingTemplate.class);
        BoardModel board = Mockito.mock(BoardModel.class);
        Mockito.lenient().when(board.getId()).thenReturn(boardId);
        Mockito.lenient().when(board.getModelName()).thenReturn(boardName);
        Mockito.lenient().when(template.getBoardModel()).thenReturn(board);
        return template;
    }

    private static BasicSettingRequest auto(List<Long> ids) {
        return new BasicSettingRequest(ids); // 편의 생성자 = AUTO
    }

    private static BasicSettingRequest specified(Long boardId, List<Long> ids) {
        return new BasicSettingRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, boardId), ids);
    }

    private void stubEnabledBoard(Long boardId) {
        BoardModel board = Mockito.mock(BoardModel.class);
        given(board.isEnabled()).willReturn(true);
        given(boardModelRepository.findByIdAndIsDeletedFalse(boardId)).willReturn(Optional.of(board));
    }

    @Test
    @DisplayName("자체 보드 selector — SPECIFIED 부존재 404 · disabled 409(field=boardModel)")
    void ownBoardGuard() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(specified(99L, List.of(1L)), CTX))
                .isInstanceOf(com.example.serverprovision.management.board.exception.BoardModelNotFoundException.class);

        BoardModel disabled = Mockito.mock(BoardModel.class);
        given(disabled.isEnabled()).willReturn(false);
        Mockito.lenient().when(disabled.getModelName()).thenReturn("MS73-HB1");
        given(boardModelRepository.findByIdAndIsDeletedFalse(6L)).willReturn(Optional.of(disabled));
        assertThatThrownBy(() -> inspector.validateReferences(specified(6L, List.of(1L)), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .hasFieldOrPropertyWithValue("fieldName", "boardModel");
    }

    @Test
    @DisplayName("템플릿 실존 — 없는 id → 404")
    void unknownTemplate_throws404() {
        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(auto(List.of(99L)), CTX))
                .isInstanceOf(BiosSettingTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("AUTO — 상이 보드 N개 통과 · 같은 보드 2개 → 400 field=biosSettingTemplateIds")
    void autoMode_boardDuplication() {
        BiosSettingTemplate ms73a = template(6L, "MS73-HB1");
        BiosSettingTemplate md72 = template(8L, "MD72-HB3");
        given(repository.findById(1L)).willReturn(Optional.of(ms73a));
        given(repository.findById(2L)).willReturn(Optional.of(md72));
        assertThatCode(() -> inspector.validateReferences(auto(List.of(1L, 2L)), CTX))
                .doesNotThrowAnyException();

        BiosSettingTemplate ms73b = template(6L, "MS73-HB1");
        given(repository.findById(3L)).willReturn(Optional.of(ms73b));
        assertThatThrownBy(() -> inspector.validateReferences(auto(List.of(1L, 3L)), CTX))
                .isInstanceOf(InvalidBiosTemplateSelectionException.class)
                .hasFieldOrPropertyWithValue("fieldName", "biosSettingTemplateIds");
    }

    @Test
    @DisplayName("SPECIFIED — 자체 selector 의 보드와 다른 보드의 템플릿 → 400 · 일치 통과")
    void specifiedMode_boardMatch() {
        stubEnabledBoard(6L);
        BiosSettingTemplate md72 = template(8L, "MD72-HB3");
        given(repository.findById(2L)).willReturn(Optional.of(md72));
        assertThatThrownBy(() -> inspector.validateReferences(specified(6L, List.of(2L)), CTX))
                .isInstanceOf(InvalidBiosTemplateSelectionException.class);

        BiosSettingTemplate ms73 = template(6L, "MS73-HB1");
        given(repository.findById(1L)).willReturn(Optional.of(ms73));
        assertThatCode(() -> inspector.validateReferences(specified(6L, List.of(1L)), CTX))
                .doesNotThrowAnyException();
    }
}
