package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.service.comps.IsoRegistrationFailureRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** E4-1-b-2 D-20 — ISO 의 등록 실패 표시 · 정의서 선택 불가 사유 · 추출 성공 시 해제 · 기록기. */
class IsoRegistrationFailureTest {

    @Test
    @DisplayName("등록 실패를 남기면 사유가 보이고 정의서에서 고를 수 없으며, 추출이 끝까지 성공하면 지워진다")
    void lifecycle() {
        ISO iso = ISO.builder().isoPath("/isos/a.iso").isEnabled(true).isDeleted(false).build();
        assertThat(iso.isRegistrationFailed()).isFalse();
        assertThat(iso.installSelectionBlockReason()).isNull();

        iso.markRegistrationFailed("사유 문장.");
        assertThat(iso.isRegistrationFailed()).isTrue();
        assertThat(iso.installSelectionBlockReason()).isEqualTo("등록에 실패한 ISO 입니다. 사유 문장.");

        iso.markExtracted();
        assertThat(iso.isRegistrationFailed()).isFalse();
        assertThat(iso.isExtractionComplete()).isTrue();
    }

    @Test
    @DisplayName("기록기 — 있는 ISO 에 사유를 남기고, 그새 사라졌으면 아무것도 하지 않는다")
    void recorder() {
        ISORepository repository = Mockito.mock(ISORepository.class);
        ISO iso = ISO.builder().isoPath("/isos/a.iso").isEnabled(true).isDeleted(false).build();
        given(repository.findById(1L)).willReturn(Optional.of(iso));
        given(repository.findById(2L)).willReturn(Optional.empty());
        IsoRegistrationFailureRecorder recorder = new IsoRegistrationFailureRecorder(repository);

        recorder.record(1L, "사유");
        recorder.record(2L, "사유");

        assertThat(iso.getRegistrationFailure()).isEqualTo("사유");
    }
}
