package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.service.IsoPreparationService.PreparedIsoPath;
import com.example.serverprovision.management.os.service.extractor.CompsExtractionResult;
import com.example.serverprovision.management.os.service.extractor.CompsExtractorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CompsExtractionService 단위 테스트 — plan 규약대로 happy 1 + 실패 2.
 * Stage S1 전환 후 {@code BackgroundJobService} 를 상대로 report/complete/fail 이 호출되는지 검증한다.
 * {@code @Async} 프록시 없이 직접 new 로 호출하므로 동기 실행되어 verify 흐름을 확인하기 쉽다.
 */
@ExtendWith(MockitoExtension.class)
class CompsExtractionServiceTest {

    @Mock CompsExtractorStrategy strategy;
    @Mock IsoPreparationService isoPreparationService;
    @Mock CompsMergeService compsMergeService;
    @Mock BackgroundJobService backgroundJobService;

    CompsExtractionService service;

    OSImage rocky;
    ISO iso;

    @BeforeEach
    void setUp() {
        service = new CompsExtractionService(
                List.of(strategy),
                isoPreparationService,
                compsMergeService,
                backgroundJobService
        );
        rocky = OSImage.builder()
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.5")
                .description("rocky 9.5")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        ReflectionTestUtils.setField(rocky, "id", 1L);

        iso = ISO.builder()
                .osImage(rocky)
                .isoPath("/data/iso/rocky95.iso")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        ReflectionTestUtils.setField(iso, "id", 2L);
    }

    @Test
    @DisplayName("happy: 전략이 지원하고 추출이 성공하면 mergeAndSave 후 complete 가 호출된다")
    void happy_callsMergeAndComplete() {
        given(isoPreparationService.prepare("/data/iso/rocky95.iso"))
                .willReturn(PreparedIsoPath.passthrough("/mnt/rocky"));
        given(strategy.supports(OSName.ROCKY_LINUX)).willReturn(true);
        given(strategy.extract("/mnt/rocky"))
                .willReturn(new CompsExtractionResult(List.of(), Map.of()));

        service.extractAsync(rocky, iso, "job-happy");

        verify(compsMergeService).mergeAndSave(eq(1L), eq(2L), any(CompsExtractionResult.class));
        verify(backgroundJobService).complete("job-happy");
        verify(backgroundJobService, never()).fail(anyString(), anyString());
    }

    @Test
    @DisplayName("실패: 지원 전략이 없는 OS 는 backgroundJobService.fail 로 끝난다")
    void unsupported_callsFail() {
        OSImage ubuntu = OSImage.builder()
                .osName(OSName.UBUNTU)
                .osVersion("22.04")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        ReflectionTestUtils.setField(ubuntu, "id", 10L);

        given(isoPreparationService.prepare(anyString()))
                .willReturn(PreparedIsoPath.passthrough("/mnt/ubuntu"));
        given(strategy.supports(OSName.UBUNTU)).willReturn(false);

        service.extractAsync(ubuntu, iso, "job-unsupp");

        verify(backgroundJobService).fail(eq("job-unsupp"), contains("Ubuntu"));
        verify(compsMergeService, never()).mergeAndSave(anyLong(), anyLong(), any());
        verify(backgroundJobService, never()).complete(anyString());
    }

    @Test
    @DisplayName("실패: 전략 extract 가 예외를 던지면 fail 이 호출되고 mergeAndSave 는 호출되지 않는다")
    void extractorThrows_callsFail() {
        given(isoPreparationService.prepare(anyString()))
                .willReturn(PreparedIsoPath.passthrough("/mnt/rocky"));
        given(strategy.supports(OSName.ROCKY_LINUX)).willReturn(true);
        given(strategy.extract(anyString()))
                .willThrow(new IllegalStateException("comps 데이터를 포함한 repomd.xml 을 찾을 수 없습니다."));

        service.extractAsync(rocky, iso, "job-fail");

        verify(backgroundJobService).fail(eq("job-fail"), contains("repomd"));
        verify(compsMergeService, never()).mergeAndSave(anyLong(), anyLong(), any());
        verify(backgroundJobService, never()).complete(anyString());
    }
}
