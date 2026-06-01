package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.lifecycle.exception.SoftDeleteRequiresIntentException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.management.common.exception.PathCorrectionFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MK3-2 (DCM3-2.1, 2.4, 2.5) — SoftDeleteIntentService 의 사전조건 / saga / forced clear 검증.
 */
class SoftDeleteIntentServiceTest {

    private ResourceExistenceChecker checker;
    private DeleteIntentRegistry registry;
    private PathReconciliationService reconciliationService;
    private MarkableScanner scanner;
    private SoftDeleteIntentService service;

    @BeforeEach
    void setUp() {
        checker = mock(ResourceExistenceChecker.class);
        registry = new DeleteIntentRegistry();
        reconciliationService = mock(PathReconciliationService.class);
        scanner = mock(MarkableScanner.class);
        given(scanner.supportedType()).willReturn(ResourceType.OS_ISO);

        service = new SoftDeleteIntentService(checker, registry, reconciliationService, List.of(scanner));
        ReflectionTestUtils.setField(service, "rejectOnMissing", true);
    }

    @Test
    @DisplayName("L2 : flag true + 자원 존재 → 통과 (예외 없음)")
    void checkPrecondition_pass() {
        TestEntity entity = new TestEntity(42L, Path.of("/opt/iso/foo.iso"));
        given(checker.exists(entity.getResourcePath())).willReturn(true);

        service.checkPrecondition(entity);
        // 예외 없으면 OK
    }

    @Test
    @DisplayName("L3 : flag true + 자원 부재 → SoftDeleteRequiresIntentException + token 발급")
    void checkPrecondition_reject() {
        TestEntity entity = new TestEntity(42L, Path.of("/opt/iso/missing.iso"));
        given(checker.exists(entity.getResourcePath())).willReturn(false);

        assertThatThrownBy(() -> service.checkPrecondition(entity))
                .isInstanceOf(SoftDeleteRequiresIntentException.class)
                .satisfies(e -> {
                    SoftDeleteRequiresIntentException sx = (SoftDeleteRequiresIntentException) e;
                    assertThat(sx.intent().resourceId()).isEqualTo(42L);
                    assertThat(sx.intent().resourceType()).isEqualTo(ResourceType.OS_ISO);
                    assertThat(sx.intent().missingPath()).isEqualTo(entity.getResourcePath());
                    assertThat(sx.intent().ghostCandidate()).isFalse(); // active 자원이라 false
                });
    }

    @Test
    @DisplayName("L1 : flag false → 검사 비활성. 자원 부재여도 예외 없이 통과 (회귀 차단)")
    void checkPrecondition_flagOff_skip() {
        ReflectionTestUtils.setField(service, "rejectOnMissing", false);
        TestEntity entity = new TestEntity(42L, Path.of("/opt/iso/missing.iso"));
        // checker.exists 자체가 호출되지 않음 — flag false 시 short-circuit
        // verify 도 가능하지만 핵심은 예외 없이 통과
        service.checkPrecondition(entity);
    }

    @Test
    @DisplayName("M2 : saga reconcileThenDelete 의 PATH_DRIFT 미발견 → PathCorrectionFailedException (즉시 fail-stop)")
    void reconcileThenDelete_noPathDrift() {
        given(reconciliationService.scanForResource(eq(ResourceType.OS_ISO), eq(42L)))
                .willReturn(List.of()); // PATH_DRIFT 없음

        assertThatThrownBy(() -> service.reconcileThenDelete(ResourceType.OS_ISO, 42L, () -> {}))
                .isInstanceOf(PathCorrectionFailedException.class);

        verify(reconciliationService, never()).persistAndForcedApply(any());
    }

    @Test
    @DisplayName("M1 : saga reconcileThenDelete 정상 — PATH_DRIFT 발견 → forced apply → normalSoftDelete 콜백")
    void reconcileThenDelete_success(@TempDir Path tmp) {
        Drift pathDrift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L)
                .kind(com.example.serverprovision.global.marker.DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath(tmp.resolve("new").toString())
                .detectedAt(Instant.now()).build();
        given(reconciliationService.scanForResource(eq(ResourceType.OS_ISO), eq(42L)))
                .willReturn(List.of(pathDrift));

        boolean[] callbackInvoked = {false};
        service.reconcileThenDelete(ResourceType.OS_ISO, 42L, () -> callbackInvoked[0] = true);

        verify(reconciliationService, times(1)).persistAndForcedApply(pathDrift);
        assertThat(callbackInvoked[0]).isTrue();
    }

    @Test
    @DisplayName("N1 : forcedClear → scanner.applyForcedClear 호출")
    void forcedClear_delegates() {
        service.forcedClear(ResourceType.OS_ISO, 42L);
        verify(scanner, times(1)).applyForcedClear(42L);
    }

    /** 테스트 전용 entity stub. LifecycleEntity 의 lifecycle 메서드 + Markable 시그니처. */
    private static class TestEntity extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;

        TestEntity(Long id, Path path) {
            this.id = id;
            this.path = path;
        }

        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return "h"; }
        @Override public String getMarkerSignature() { return null; }
        @Override public void reissueMarker(String hash, String signature) {}
    }
}
