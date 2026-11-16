package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** HF26 — 메인보드 영구 삭제를 막는 BIOS 세팅 템플릿. */
@ExtendWith(MockitoExtension.class)
class BiosSettingTemplateBoardReferenceTest {

    @Mock BiosSettingTemplateRepository repository;
    @InjectMocks BiosSettingTemplateBoardReference reference;

    @Test
    @DisplayName("그 보드의 템플릿이 있으면 개수를 적고, 없으면 null")
    void countsTemplates() {
        given(repository.countByBoardModel_Id(3L)).willReturn(1L);
        given(repository.countByBoardModel_Id(4L)).willReturn(0L);

        assertThat(reference.remainingReference(3L)).isEqualTo("BIOS 세팅 템플릿 1 개");
        assertThat(reference.remainingReference(4L)).isNull();
    }
}
