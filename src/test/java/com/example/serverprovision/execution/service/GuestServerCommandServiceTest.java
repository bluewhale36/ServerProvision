package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
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
 * 유니크 위임, 404 를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerCommandServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @InjectMocks GuestServerCommandService service;

    private GuestServer server(UUID id) {
        return GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
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
}
