package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * S5-7 — Rocky Linux / CentOS ISO 등록 후 자동 comps 추출 트리거 검증.
 */
@ExtendWith(MockitoExtension.class)
class RhelPostCreationTaskStrategyTest {

    @Mock
    private CompsExtractionLauncher compsExtractionLauncher;

    @InjectMocks
    private RhelPostCreationTaskStrategy strategy;

    @Test
    @DisplayName("supports : RHEL_BASED 만 처리한다")
    void supportsRhelOnly() {
        assertThat(strategy.supports(OSFamily.RHEL_BASED)).isTrue();
        assertThat(strategy.supports(OSFamily.DEBIAN_BASED)).isFalse();
        assertThat(strategy.supports(OSFamily.WINDOWS_BASED)).isFalse();
    }

    @Test
    @DisplayName("trigger : Rocky ISO → CompsExtractionLauncher.startExtraction 호출 + jobId 반환")
    void triggerRockyDelegates() {
        OSImage osImage = OSImage.builder().osName(OSName.ROCKY_LINUX).osVersion("9.4").build();
        // 테스트용 — @Builder 의 id 는 reflection 없이 직접 set 불가. ID 가 0L 인 mock 으로 진행.
        ISO iso = ISO.builder().osImage(osImage).isoPath("/opt/iso/rocky/9/dvd.iso").build();

        given(compsExtractionLauncher.startExtraction(any(), any())).willReturn("comps-job-uuid");

        String result = strategy.trigger(osImage, iso);

        assertThat(result).isEqualTo("comps-job-uuid");
        verify(compsExtractionLauncher).startExtraction(osImage.getId(), iso.getId());
    }

    @Test
    @DisplayName("trigger : CentOS 도 동일하게 위임")
    void triggerCentOsDelegates() {
        OSImage osImage = OSImage.builder().osName(OSName.CENTOS).osVersion("7.9").build();
        ISO iso = ISO.builder().osImage(osImage).isoPath("/opt/iso/centos/7/dvd.iso").build();
        given(compsExtractionLauncher.startExtraction(any(), any())).willReturn("comps-job-uuid-2");

        String result = strategy.trigger(osImage, iso);

        assertThat(result).isEqualTo("comps-job-uuid-2");
    }

    // Mockito 의 ArgumentMatchers.any() 정적 import 누락 회피용 wildcard
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
