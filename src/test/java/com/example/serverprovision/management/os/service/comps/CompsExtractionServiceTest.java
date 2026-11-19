package com.example.serverprovision.management.os.service.comps;

import com.example.serverprovision.management.os.service.iso.IsoPreparationService;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService.PreparedIsoPath;
import com.example.serverprovision.management.os.service.comps.extractor.CompsExtractionResult;
import com.example.serverprovision.management.os.service.comps.extractor.CompsExtractorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
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

    OSMetadata rocky;
    ISO iso;

    // E4-1-b-2 D-20 — Rocky 추출은 ISO 루트의 트리 설명서 버전을 등록 OS(9.5)와 대조한다. 준비된 ISO 루트를 흉내 낸다.
    @TempDir Path isoRoot;
    private String rockyRoot;

    @Mock IsoRegistrationFailureRecorder registrationFailureRecorder;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(isoRoot.resolve(".treeinfo"), "[release]\nname = Rocky Linux\nversion = 9.5\n");
        rockyRoot = isoRoot.toString();
        service = new CompsExtractionService(
                List.of(strategy),
                isoPreparationService,
                compsMergeService,
                backgroundJobService,
                registrationFailureRecorder
        );
        rocky = OSMetadata.builder()
                .osName(OSName.ROCKY_LINUX_9)
                .osVersion("9.5")
                .description("rocky 9.5")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        ReflectionTestUtils.setField(rocky, "id", 1L);

        iso = ISO.builder()
                .osMetadata(rocky)
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
                .willReturn(PreparedIsoPath.passthrough(rockyRoot));
        given(strategy.supports(OSName.ROCKY_LINUX_9)).willReturn(true);
        given(strategy.extract(rockyRoot))
                .willReturn(new CompsExtractionResult(List.of(), Map.of()));

        service.extractAsync(rocky, iso, "job-happy");

        verify(compsMergeService).mergeAndSave(eq(1L), eq(2L), any(CompsExtractionResult.class));
        verify(backgroundJobService).complete("job-happy");
        verify(backgroundJobService, never()).fail(anyString(), anyString());
    }

    @Test
    @DisplayName("실패: 지원 전략이 없는 OS 는 backgroundJobService.fail 로 끝난다")
    void unsupported_callsFail() {
        OSMetadata ubuntu = OSMetadata.builder()
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
                .willReturn(PreparedIsoPath.passthrough(rockyRoot));
        given(strategy.supports(OSName.ROCKY_LINUX_9)).willReturn(true);
        given(strategy.extract(anyString()))
                .willThrow(new IllegalStateException("comps 데이터를 포함한 repomd.xml 을 찾을 수 없습니다."));

        service.extractAsync(rocky, iso, "job-fail");

        verify(backgroundJobService).fail(eq("job-fail"), contains("repomd"));
        verify(compsMergeService, never()).mergeAndSave(anyLong(), anyLong(), any());
        verify(backgroundJobService, never()).complete(anyString());
    }

    // ==== E4-1-b-2 D-20 — ISO 버전 확인 ==========================================================

    @Test
    @DisplayName("버전 불일치: ISO 의 실제 버전(11.0)이 등록 OS(9.5)와 다르면 fail(문구) · 저장 · 전략 미호출")
    void releaseMismatch_callsFail() throws Exception {
        Files.writeString(isoRoot.resolve(".treeinfo"), "[release]\nname = Rocky Linux\nversion = 11.0\n");
        given(isoPreparationService.prepare(anyString())).willReturn(PreparedIsoPath.passthrough(rockyRoot));

        service.extractAsync(rocky, iso, "job-mismatch");

        verify(backgroundJobService).fail(eq("job-mismatch"),
                eq("ISO 의 실제 버전(Rocky Linux 11.0)과 등록한 OS 버전(Rocky Linux 9.5)이 다릅니다. 맞는 OS 아래에 다시 등록해야 합니다."));
        verify(strategy, never()).extract(anyString());
        verify(compsMergeService, never()).mergeAndSave(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("트리 설명서 없음: Rocky ISO 루트에 .treeinfo 가 없으면 fail(RHEL 계열 설치 ISO 확인 문구)")
    void treeinfoMissing_callsFail() throws Exception {
        Files.delete(isoRoot.resolve(".treeinfo"));
        given(isoPreparationService.prepare(anyString())).willReturn(PreparedIsoPath.passthrough(rockyRoot));

        service.extractAsync(rocky, iso, "job-notree");

        verify(backgroundJobService).fail(eq("job-notree"), contains("RHEL 계열 설치 ISO 인지 확인해야 합니다"));
        verify(compsMergeService, never()).mergeAndSave(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("CentOS 는 버전 확인을 건너뛴다(.treeinfo 없어도 추출 진행)")
    void centosSkipsReleaseCheck() throws Exception {
        Files.delete(isoRoot.resolve(".treeinfo"));
        OSMetadata centos = OSMetadata.builder().osName(OSName.CENTOS).osVersion("7.9").isEnabled(true).isDeleted(false).build();
        ReflectionTestUtils.setField(centos, "id", 3L);
        given(isoPreparationService.prepare(anyString())).willReturn(PreparedIsoPath.passthrough(rockyRoot));
        given(strategy.supports(OSName.CENTOS)).willReturn(true);
        given(strategy.extract(rockyRoot)).willReturn(new CompsExtractionResult(List.of(), Map.of()));

        service.extractAsync(centos, iso, "job-centos");

        verify(compsMergeService).mergeAndSave(eq(3L), eq(2L), any(CompsExtractionResult.class));
        verify(backgroundJobService).complete("job-centos");
    }

    @Test
    @DisplayName("D-20 — 버전 불일치 · 트리 설명서 없음은 ISO 를 '등록 실패' 로 남긴다(사유 = 작업 실패 문장), 성공은 남기지 않는다")
    void releaseFailure_recordsRegistrationFailure() throws Exception {
        given(isoPreparationService.prepare(anyString())).willReturn(PreparedIsoPath.passthrough(rockyRoot));
        Files.writeString(isoRoot.resolve(".treeinfo"), "[release]\nname = Rocky Linux\nversion = 11.0\n");
        service.extractAsync(rocky, iso, "job-a");
        verify(registrationFailureRecorder).record(2L,
                "ISO 의 실제 버전(Rocky Linux 11.0)과 등록한 OS 버전(Rocky Linux 9.5)이 다릅니다. 맞는 OS 아래에 다시 등록해야 합니다.");

        Files.delete(isoRoot.resolve(".treeinfo"));
        service.extractAsync(rocky, iso, "job-b");
        verify(registrationFailureRecorder).record(2L, "ISO 에서 배포판 정보를 찾지 못했습니다. RHEL 계열 설치 ISO 인지 확인해야 합니다.");
    }

    @Test
    @DisplayName("D-20 — 버전이 맞으면 등록 실패를 남기지 않는다")
    void releaseMatch_doesNotRecord() {
        given(isoPreparationService.prepare(anyString())).willReturn(PreparedIsoPath.passthrough(rockyRoot));
        given(strategy.supports(OSName.ROCKY_LINUX_9)).willReturn(true);
        given(strategy.extract(rockyRoot)).willReturn(new CompsExtractionResult(List.of(), Map.of()));

        service.extractAsync(rocky, iso, "job-ok");

        verify(registrationFailureRecorder, never()).record(anyLong(), anyString());
    }
}
