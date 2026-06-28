package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 상태 변경(인라인 수정 · 회수) application service (U1 §D11).
 * 운영자 입력 4필드는 모두 단일 테이블 guest_server(§D1). 유니크(name / serial_number) 충돌 여부는 컨트롤러가
 * boolean 질의로 미리 확인해 BindingResult 인라인 표시한다(예외=프로그램 예외 전용).
 */
@Service
@RequiredArgsConstructor
public class GuestServerCommandService {

    private final GuestServerRepository guestServerRepository;

    @Transactional(readOnly = true)
    public boolean isNameTakenByOther(UUID id, String name) {
        return guestServerRepository.existsByNameAndIdNot(name, id);
    }

    @Transactional(readOnly = true)
    public boolean isSerialTakenByOther(UUID id, String serialNumber) {
        return guestServerRepository.existsBySerialNumberAndIdNot(serialNumber, id);
    }

    /**
     * 이름·사내 모델명·사내 시리얼·메모(모두 guest_server)를 한 트랜잭션으로 갱신. 빈 입력은 null 로 정규화.
     */
    @Transactional
    public void update(UUID id, UpdateGuestServerRequest req) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        server.updateOperatorInfo(
                blankToNull(req.name()),
                blankToNull(req.modelName()),
                blankToNull(req.serialNumber()),
                blankToNull(req.memo()));
    }

    /**
     * 서버 회수 — decommissioned_at 기록(멱등). 운영 상태는 이 마커에서 도출(§D4).
     */
    @Transactional
    public void decommission(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        server.decommission(LocalDateTime.now());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
