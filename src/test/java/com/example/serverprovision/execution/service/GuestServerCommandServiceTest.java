package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U1 CP4 — {@link GuestServerCommandService} 단위 테스트. 인라인 수정(4필드, blank→null), 회수(멱등),
 * 유니크 위임, 404 를 검증한다. E1-0a — 프로비저닝 개시(happy / 재개시 409 / 회수 409) 추가.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerCommandServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @InjectMocks GuestServerCommandService service;

    private GuestServer server(UUID id) {
        return GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
    }

    private ProvisioningProgress seedProgress() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING)
                .lastTransitionAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("update — 4필드 갱신 + 빈 입력은 null 정규화")
    void update_appliesAndNormalizes() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));

        service.update(id, new UpdateGuestServerRequest(" web-01 ", "RE2108", "  ", null));

        assertThat(s.getName()).isEqualTo("web-01");        // trim
        assertThat(s.getModelName()).isEqualTo("RE2108");
        assertThat(s.getSerialNumber()).isNull();           // blank → null
        assertThat(s.getMemo()).isNull();
    }

    @Test
    @DisplayName("update — 없는 id → GuestServerNotFoundException")
    void update_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateGuestServerRequest("x", null, null, null)))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    @Test
    @DisplayName("decommission — 회수 시각 기록")
    void decommission_recordsTime() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));

        service.decommission(id);

        assertThat(s.getDecommissionedAt()).isNotNull();
    }

    @Test
    @DisplayName("decommission — 이미 회수된 서버는 최초 시각 보존(멱등)")
    void decommission_idempotent() {
        UUID id = UUID.randomUUID();
        LocalDateTime first = LocalDateTime.now().minusDays(1);
        GuestServer s = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).decommissionedAt(first).build();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));

        service.decommission(id);

        assertThat(s.getDecommissionedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("isNameTakenByOther / isSerialTakenByOther — repo 위임")
    void uniquenessQueries_delegate() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.existsByNameAndIdNot("dup", id)).willReturn(true);
        given(guestServerRepository.existsBySerialNumberAndIdNot("S1", id)).willReturn(false);

        assertThat(service.isNameTakenByOther(id, "dup")).isTrue();
        assertThat(service.isSerialTakenByOther(id, "S1")).isFalse();
    }

    // ==== 프로비저닝 개시 (E1-0a, DEC-26) ======================================

    @Test
    @DisplayName("startProvisioning — startedAt 기록 (개시 SSOT 가드 통과)")
    void start_records() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = seedProgress();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        service.startProvisioning(id);

        assertThat(progress.isStarted()).isTrue();
    }

    @Test
    @DisplayName("startProvisioning — 이미 개시된 서버 → ProvisioningStartRejectedException(409 안전망)")
    void start_alreadyStarted_rejected() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = seedProgress();
        progress.start(LocalDateTime.now());
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(ProvisioningStartRejectedException.class)
                .hasMessageContaining("이미 개시");
    }

    @Test
    @DisplayName("startProvisioning — 회수된 서버 → ProvisioningStartRejectedException(409 안전망)")
    void start_decommissioned_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer decommissioned = GuestServer.builder()
                .id(id).systemUUID(UUID.randomUUID()).decommissionedAt(LocalDateTime.now()).build();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(decommissioned));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(seedProgress()));

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(ProvisioningStartRejectedException.class)
                .hasMessageContaining("회수");
    }

    @Test
    @DisplayName("startProvisioning — 없는 id → GuestServerNotFoundException")
    void start_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(GuestServerNotFoundException.class);
    }
}
