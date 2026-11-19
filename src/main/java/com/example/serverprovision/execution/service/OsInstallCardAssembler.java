package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import com.example.serverprovision.execution.entity.ProvisioningHistory;

import java.util.Optional;
import java.util.UUID;

/**
 * 서버 상세 OS 설치 카드의 계열 내용 조립기 SPI(E4-1-b-4-2). 계열마다 하나를 두고, 조회 서비스는 카드 계열의 조립기만 부른다 —
 * 새 계열은 조립기와 카드 조각을 더할 뿐 조회 서비스와 템플릿의 분기를 늘리지 않는다.
 */
public interface OsInstallCardAssembler {

    /** 이 조립기가 맡는 계열(원장 origin). */
    OsInstallOrigin origin();

    GuestServerDetailResponse.OsInstallCardDetail assemble(Context context);

    /**
     * @param latest    카드 계열이 적은 마지막 OS 설치 행
     * @param served    설치 중이거나 완료 보고로 닫힌 행(실패 뒤에는 empty)
     * @param completed 완료 보고로 닫힌 행
     */
    record Context(UUID guestServerId, Optional<ProvisioningHistory> latest, Optional<ProvisioningHistory> served,
                   Optional<ProvisioningHistory> completed) {
    }
}
