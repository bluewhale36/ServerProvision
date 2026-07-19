package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /boot} 진입 orchestration(E1-0b) — 멱등 등록(+토큰 lazy 발급) 후 dispatch 판정까지를
 * 한 트랜잭션으로 잇는다. 컨트롤러가 repository 를 만지지 않게 하는 경계(레이어 규약)이자,
 * 등록과 판정 사이의 상태가 반드시 같은 스냅샷이 되게 하는 지점이다.
 */
@Service
@RequiredArgsConstructor
public class BootService {

    private final GuestServerRegistrationService registrationService;
    private final ProvisioningProgressRepository provisioningProgressRepository;
    private final BootScriptDispatcher bootScriptDispatcher;

    @Transactional
    public String boot(BootIPXEInfoRequest request, String rebootQuery) {
        GuestServer server = registrationService.initialRegistry(request);
        server.touchSeen(java.time.LocalDateTime.now());   // 접촉 관찰 로그(E1-2, DEC-32) — 판정 입력 아님
        ProvisioningProgress progress = provisioningProgressRepository.findByGuestServer_Id(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
        return bootScriptDispatcher.dispatch(server, progress, rebootQuery == null ? "" : rebootQuery);
    }
}
