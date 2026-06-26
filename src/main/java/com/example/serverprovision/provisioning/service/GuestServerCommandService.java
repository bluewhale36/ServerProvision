package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerCustom;
import com.example.serverprovision.execution.repository.GuestServerCustomRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.provisioning.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.provisioning.exception.GuestServerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 게스트 서버 상세 화면 인라인 수정(쓰기) 서비스. 읽기 경로는 {@link GuestServerQueryService} 가 담당한다.
 * 유니크 컬럼(name / serial_number) 중복 여부는 컨트롤러가 본 서비스의 boolean 질의로 미리 확인해
 * BindingResult 필드 에러로 인라인 표시한다.
 */
@Service
@RequiredArgsConstructor
public class GuestServerCommandService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerCustomRepository customRepository;

    @Transactional(readOnly = true)
    public boolean isNameTakenByOther(UUID id, String name) {
        return guestServerRepository.existsByNameAndIdNot(name, id);
    }

    @Transactional(readOnly = true)
    public boolean isSerialTakenByOther(UUID id, String serialNumber) {
        return customRepository.existsByProductSerialNumberAndGuestServer_IdNot(serialNumber, id);
    }

    /**
     * 이름·메모(guest_server) + 사내 모델명·시리얼(guest_server_custom) 을 한 트랜잭션으로 갱신.
     * 빈 입력은 null 로 정규화한다.
     */
    @Transactional
    public void update(UUID id, UpdateGuestServerRequest req) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        server.updateBasicInfo(blankToNull(req.name()), blankToNull(req.memo()));

        GuestServerCustom custom = customRepository.findByGuestServer_Id(id).orElse(null);
        if (custom == null) {
            // 등록 시 항상 함께 생성되므로 일반적으로 도달하지 않는 방어 분기.
            custom = GuestServerCustom.builder()
                    .id(UuidVersion7Strategy.INSTANCE.generateUuid(null))
                    .guestServer(server)
                    .build();
        }
        custom.updateIdentity(blankToNull(req.productModelName()), blankToNull(req.productSerialNumber()));
        customRepository.save(custom);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
