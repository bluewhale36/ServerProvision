package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.os.dto.request.OSMetadataCreateRequest;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R1-6 — {@link OSMetadataNudgeService} 단위 테스트.
 *
 * <p>R1-6 흡수 후 nudge 영속화 4 메서드 (completePendingFromNudge / purgeForNudge /
 * buildNudgePayload / persistNew) 와 confirm 진입점 (proceed / replace / cancel) 의 분기를 커버한다.
 * 의존은 {@link NudgeRegistry} + {@link OSMetadataRepository} 두 mock 뿐 — self-contained 구조 확인.</p>
 */
@ExtendWith(MockitoExtension.class)
class OSMetadataNudgeServiceTest {

    @Mock NudgeRegistry nudgeRegistry;
    @Mock OSMetadataRepository osMetadataRepository;
    @InjectMocks OSMetadataNudgeService osMetadataNudgeService;

    // ---- 픽스처 헬퍼 -----------------------------------------------------

    /** OS_IMAGE intent payload 를 담은 활성 nudge 세션. attributes 키는 흡수 service 어휘 그대로. */
    private NudgeSession osImageSession(UUID nudgeId, List<Long> conflictTargetIds) {
        IntentMetaNudgePayload payload = new IntentMetaNudgePayload(Map.of(
                "osName", OSName.ROCKY_LINUX.name(),
                "osVersion", "9.6",
                "description", "충돌 후 신규 등록"
        ));
        Instant now = Instant.now();
        return new NudgeSession(
                nudgeId,
                NudgeResourceType.OS_IMAGE,
                null,
                conflictTargetIds,
                payload,
                now,
                now.plusSeconds(300)
        );
    }

    /** soft-deleted (또는 deprecated) 충돌 후보 row — replace purge / nudge 후보 합성에 사용. */
    private OSMetadata softDeletedRow(Long id) {
        return OSMetadata.builder()
                .id(id).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(false).isDeprecated(false).isDeleted(true).build();
    }

    // ---- proceed --------------------------------------------------------

    @Test
    @DisplayName("proceed(happy) : 충돌 후보 보존 + persistNew 로 신규 ACTIVE 영속화 후 세션을 consume 한다")
    void proceed_whenSessionValid_persistsNewAndConsumesSession() {
        // given
        UUID nudgeId = UUID.randomUUID();
        NudgeSession session = osImageSession(nudgeId, List.of(8L));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        // confirm 시점 race 재검사 — 활성 자원 부재.
        given(osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(false);
        given(osMetadataRepository.save(any(OSMetadata.class)))
                .willAnswer(inv -> OSMetadata.builder()
                        .id(100L)
                        .osName(((OSMetadata) inv.getArgument(0)).getOsName())
                        .osVersion(((OSMetadata) inv.getArgument(0)).getOsVersion())
                        .description(((OSMetadata) inv.getArgument(0)).getDescription())
                        .build());
        given(nudgeRegistry.remove(nudgeId)).willReturn(true);

        // when
        Long newId = osMetadataNudgeService.proceed(nudgeId);

        // then
        assertThat(newId).isEqualTo(100L);
        // 충돌 후보는 hard-delete 되지 않고 보존 — proceed 는 신규만 등록한다.
        verify(osMetadataRepository, never()).delete(any());
        verify(nudgeRegistry).remove(nudgeId);
    }

    @Test
    @DisplayName("proceed(wrong payload type) : 세션 payload 가 IntentMetaNudgePayload 가 아니면 IllegalOSMetadataStateException")
    void proceed_whenPayloadNotIntentMeta_throwsIllegalState() {
        // given — OS_IMAGE 세션이지만 payload 가 ContentNudgePayload (단계 B 해시 충돌용).
        UUID nudgeId = UUID.randomUUID();
        Instant now = Instant.now();
        NudgePayload contentPayload =
                new ContentNudgePayload("rocky", "9.6", "hash", "/tmp/x.iso", Map.of());
        NudgeSession session = new NudgeSession(
                nudgeId, NudgeResourceType.OS_IMAGE, null, List.of(),
                contentPayload, now, now.plusSeconds(300));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.proceed(nudgeId))
                .isInstanceOf(IllegalOSMetadataStateException.class);
        // payload 불량으로 조기 실패 — save / 세션 consume 없음.
        verify(osMetadataRepository, never()).save(any());
        verify(nudgeRegistry, never()).remove(any());
    }

    @Test
    @DisplayName("proceed(race) : confirm 시점 같은 (osName, osVersion) 활성 자원이 생기면 DuplicateOSMetadataException")
    void proceed_whenActiveRecreatedConcurrently_throwsDuplicate() {
        // given
        UUID nudgeId = UUID.randomUUID();
        NudgeSession session = osImageSession(nudgeId, List.of(8L));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        // race — 다른 트랜잭션이 confirm 직전 같은 메타로 활성 자원을 만든 상태.
        given(osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.proceed(nudgeId))
                .isInstanceOf(DuplicateOSMetadataException.class);
        verify(osMetadataRepository, never()).save(any());
        verify(nudgeRegistry, never()).remove(any());
    }

    // ---- replace --------------------------------------------------------

    @Test
    @DisplayName("replace(happy) : 충돌 후보를 purge 한 뒤 신규를 등록하고 세션을 consume 한다 (purge → persist 순서)")
    void replace_whenTargetValid_purgesThenPersists() {
        // given
        UUID nudgeId = UUID.randomUUID();
        Long targetId = 8L;
        NudgeSession session = osImageSession(nudgeId, List.of(targetId));
        OSMetadata target = softDeletedRow(targetId);
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        given(osMetadataRepository.findById(targetId)).willReturn(Optional.of(target));
        given(osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(false);
        given(osMetadataRepository.save(any(OSMetadata.class)))
                .willAnswer(inv -> OSMetadata.builder().id(101L)
                        .osName(OSName.ROCKY_LINUX).osVersion("9.6").build());
        given(nudgeRegistry.remove(nudgeId)).willReturn(true);

        // when
        Long newId = osMetadataNudgeService.replace(nudgeId, targetId);

        // then
        assertThat(newId).isEqualTo(101L);
        // purge(delete) → persist(save) 순서가 한 트랜잭션 안에서 보장되어야 한다.
        InOrder inOrder = inOrder(osMetadataRepository);
        inOrder.verify(osMetadataRepository).delete(target);
        inOrder.verify(osMetadataRepository).save(any(OSMetadata.class));
        verify(nudgeRegistry).remove(nudgeId);
    }

    @Test
    @DisplayName("replace(invalid target) : targetId 가 세션의 충돌 후보에 없으면 InvalidReplaceTargetException")
    void replace_whenTargetNotInConflictCandidates_throwsInvalidReplaceTarget() {
        // given — 세션 후보는 8L 뿐인데 9L 로 replace 요청.
        UUID nudgeId = UUID.randomUUID();
        NudgeSession session = osImageSession(nudgeId, List.of(8L));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.replace(nudgeId, 9L))
                .isInstanceOf(InvalidReplaceTargetException.class);
        // 후보 가드에서 조기 거절 — lookup / delete / save 없음.
        verify(osMetadataRepository, never()).findById(any());
        verify(osMetadataRepository, never()).delete(any());
        verify(nudgeRegistry, never()).remove(any());
    }

    @Test
    @DisplayName("replace(target 미존재) : 후보 목록엔 있으나 row lookup 이 empty 면 OSMetadataNotFoundException")
    void replace_whenTargetRowMissing_throwsNotFound() {
        // given
        UUID nudgeId = UUID.randomUUID();
        Long targetId = 8L;
        NudgeSession session = osImageSession(nudgeId, List.of(targetId));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        given(osMetadataRepository.findById(targetId)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.replace(nudgeId, targetId))
                .isInstanceOf(OSMetadataNotFoundException.class);
        verify(osMetadataRepository, never()).delete(any());
        verify(nudgeRegistry, never()).remove(any());
    }

    // ---- purgeForNudge --------------------------------------------------

    @Test
    @DisplayName("purgeForNudge(활성 target) : isDeleted / isDeprecated 둘 다 false 면 IllegalOSMetadataStateException")
    void purgeForNudge_whenTargetActive_throwsIllegalState() {
        // given — 활성 자원 (soft-deleted/deprecated 가 아님) 은 replace 대상이 될 수 없다.
        OSMetadata activeTarget = OSMetadata.builder()
                .id(7L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(true).isDeprecated(false).isDeleted(false).build();

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.purgeForNudge(activeTarget))
                .isInstanceOf(IllegalOSMetadataStateException.class);
        verify(osMetadataRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeForNudge(soft-deleted target) : isDeleted 자원은 repository.delete 로 hard-delete 한다")
    void purgeForNudge_whenTargetSoftDeleted_deletesRow() {
        // given
        OSMetadata target = softDeletedRow(8L);

        // when
        osMetadataNudgeService.purgeForNudge(target);

        // then
        verify(osMetadataRepository).delete(target);
    }

    @Test
    @DisplayName("purgeForNudge(deprecated target) : isDeprecated 자원도 hard-delete 대상이다")
    void purgeForNudge_whenTargetDeprecated_deletesRow() {
        // given — deprecated 이지만 아직 soft-delete 는 아닌 후보.
        OSMetadata deprecatedTarget = OSMetadata.builder()
                .id(9L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(false).isDeprecated(true).isDeleted(false).build();

        // when
        osMetadataNudgeService.purgeForNudge(deprecatedTarget);

        // then
        verify(osMetadataRepository).delete(deprecatedTarget);
    }

    // ---- completePendingFromNudge --------------------------------------

    @Test
    @DisplayName("completePendingFromNudge(happy) : payload attributes 에서 osName/osVersion/description 을 복원해 persistNew 로 위임한다")
    void completePendingFromNudge_restoresAttributesAndPersists() {
        // given
        NudgeSession session = osImageSession(UUID.randomUUID(), List.of(8L));
        given(osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(false);
        given(osMetadataRepository.save(any(OSMetadata.class)))
                .willAnswer(inv -> OSMetadata.builder()
                        .id(102L)
                        .osName(((OSMetadata) inv.getArgument(0)).getOsName())
                        .osVersion(((OSMetadata) inv.getArgument(0)).getOsVersion())
                        .description(((OSMetadata) inv.getArgument(0)).getDescription())
                        .build());

        // when
        Long newId = osMetadataNudgeService.completePendingFromNudge(session);

        // then — payload 의 메타가 그대로 새 row 로 복원됐는지 save 인자로 검증.
        assertThat(newId).isEqualTo(102L);
        verify(osMetadataRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getOsName() == OSName.ROCKY_LINUX
                        && "9.6".equals(saved.getOsVersion())
                        && "충돌 후 신규 등록".equals(saved.getDescription())));
    }

    // ---- buildNudgePayload ---------------------------------------------

    @Test
    @DisplayName("buildNudgePayload : nudgeRegistry.register 로 세션을 등록하고 후보 entry 를 담은 NudgeRequiredResponse 를 조립한다")
    void buildNudgePayload_registersSessionAndAssemblesResponse() {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        OSMetadataCreateRequest request =
                new OSMetadataCreateRequest(OSName.ROCKY_LINUX, "9.6", "최신 마이너");
        OSMetadata candidate = softDeletedRow(8L);
        NudgeSession registered = new NudgeSession(
                sessionId, NudgeResourceType.OS_IMAGE, null, List.of(8L),
                new IntentMetaNudgePayload(Map.of()), Instant.now(), expiresAt);
        given(nudgeRegistry.register(
                org.mockito.ArgumentMatchers.eq(NudgeResourceType.OS_IMAGE),
                org.mockito.ArgumentMatchers.isNull(),
                any(),
                any(IntentMetaNudgePayload.class)))
                .willReturn(registered);

        // when
        NudgeRequiredResponse response = osMetadataNudgeService.buildNudgePayload(request, List.of(candidate));

        // then
        verify(nudgeRegistry).register(
                org.mockito.ArgumentMatchers.eq(NudgeResourceType.OS_IMAGE),
                org.mockito.ArgumentMatchers.isNull(),
                any(),
                any(IntentMetaNudgePayload.class));
        assertThat(response.code()).isEqualTo("NUDGE_REQUIRED");
        assertThat(response.nudgeId()).isEqualTo(sessionId);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.conflicts()).hasSize(1);
        assertThat(response.conflicts().get(0).id()).isEqualTo(8L);
    }

    // ---- persistNew -----------------------------------------------------

    @Test
    @DisplayName("persistNew(happy) : osMetadataRepository.save 를 호출하고 생성된 id 를 반환한다")
    void persistNew_savesAndReturnsGeneratedId() {
        // given
        given(osMetadataRepository.save(any(OSMetadata.class)))
                .willAnswer(inv -> OSMetadata.builder()
                        .id(103L)
                        .osName(((OSMetadata) inv.getArgument(0)).getOsName())
                        .osVersion(((OSMetadata) inv.getArgument(0)).getOsVersion())
                        .description(((OSMetadata) inv.getArgument(0)).getDescription())
                        .build());

        // when
        Long newId = osMetadataNudgeService.persistNew(OSName.ROCKY_LINUX, "9.6", "최신 마이너");

        // then
        assertThat(newId).isEqualTo(103L);
        verify(osMetadataRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getOsName() == OSName.ROCKY_LINUX
                        && "9.6".equals(saved.getOsVersion())
                        && "최신 마이너".equals(saved.getDescription())));
    }

    // ---- cancel ---------------------------------------------------------

    @Test
    @DisplayName("cancel(happy) : 메타 단독 세션은 정리할 임시 파일이 없으므로 세션만 consume 한다")
    void cancel_whenSessionValid_consumesSessionOnly() {
        // given
        UUID nudgeId = UUID.randomUUID();
        NudgeSession session = osImageSession(nudgeId, List.of(8L));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        given(nudgeRegistry.remove(nudgeId)).willReturn(true);

        // when
        osMetadataNudgeService.cancel(nudgeId);

        // then — 파일 정리 없음, 영속화 없음, 세션만 회수.
        verify(nudgeRegistry).remove(nudgeId);
        verify(osMetadataRepository, never()).save(any());
        verify(osMetadataRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancel(이미 해결된 세션) : consumeSession 이 false 면 NudgeAlreadyResolvedException")
    void cancel_whenAlreadyResolved_throwsAlreadyResolved() {
        // given — require 는 통과하나 remove 가 false (이미 다른 confirm 이 회수한 세션).
        UUID nudgeId = UUID.randomUUID();
        NudgeSession session = osImageSession(nudgeId, List.of(8L));
        given(nudgeRegistry.require(nudgeId)).willReturn(session);
        given(nudgeRegistry.remove(nudgeId)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> osMetadataNudgeService.cancel(nudgeId))
                .isInstanceOf(NudgeAlreadyResolvedException.class);
    }
}
