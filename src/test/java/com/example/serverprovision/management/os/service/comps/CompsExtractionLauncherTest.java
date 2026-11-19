package com.example.serverprovision.management.os.service.comps;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.UnsupportedExtractionException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/** S23-1 — 추출 런처의 동기 가드: 추출 능력이 없는 OS 는 작업을 띄우기 전에 거절한다(화면은 같은 판정으로 [추출] 을 숨긴다). */
@ExtendWith(MockitoExtension.class)
class CompsExtractionLauncherTest {

    @Mock OSMetadataRepository osMetadataRepository;
    @Mock ISORepository isoRepository;
    @Mock BackgroundJobService backgroundJobService;
    @Mock CompsExtractionService compsExtractionService;
    @InjectMocks CompsExtractionLauncher launcher;

    private void stubOsAndIso(OSName osName, String version) {
        OSMetadata os = OSMetadata.builder().id(1L).osName(osName).osVersion(version)
                .isEnabled(true).isDeleted(false).isDeprecated(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(os));
        given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(
                ISO.builder().id(2L).osMetadata(os).isoPath("/opt/iso/2.iso").checksum("h").manifestHash("h")
                        .isEnabled(true).isDeleted(false).isDeprecated(false).build()));
    }

    @Test
    @DisplayName("Windows Server 2025 ISO → UnsupportedExtractionException, 작업 등록 · 비동기 추출 없음")
    void windows_rejectedBeforeJob() {
        stubOsAndIso(OSName.WINDOWS_SERVER_2025, "2025");

        assertThatThrownBy(() -> launcher.startExtraction(1L, 2L)).isInstanceOf(UnsupportedExtractionException.class);
        verifyNoInteractions(backgroundJobService, compsExtractionService);
    }

    @Test
    @DisplayName("미지원 RHEL 상수(CentOS) ISO → UnsupportedExtractionException, 작업 없음")
    void legacyRhel_rejectedBeforeJob() {
        stubOsAndIso(OSName.CENTOS, "7.9");

        assertThatThrownBy(() -> launcher.startExtraction(1L, 2L)).isInstanceOf(UnsupportedExtractionException.class);
        verifyNoInteractions(backgroundJobService, compsExtractionService);
    }
}
