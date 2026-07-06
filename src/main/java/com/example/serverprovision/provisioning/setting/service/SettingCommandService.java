package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;

/**
 * 세팅 정의서 쓰기 계약 — 컨트롤러는 이 인터페이스만 본다.
 * (U2-1 스텁 → U2-3 에서 {@link JpaSettingCommandService} 실영속 구현으로 대체 완료.
 *  상태 가드 없음 — 정의서는 수정 자유 템플릿, Q1)
 */
public interface SettingCommandService {

    SettingSaveResponse create(SettingSaveRequest request);

    /**
     * @throws SettingNotFoundException 해당 id 의 정의서가 없을 때 (advice 404)
     */
    SettingSaveResponse update(Long id, SettingSaveRequest request);
}
