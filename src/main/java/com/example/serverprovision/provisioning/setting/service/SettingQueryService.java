package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.provisioning.setting.dto.response.BiosTemplateOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.PartitionPresetResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;

import java.util.List;

/**
 * 세팅 정의서 조회 계약 — 컨트롤러는 이 인터페이스만 본다.
 * (U2-1 스텁 → U2-3 에서 {@link JpaSettingQueryService} 실영속 구현으로 대체 완료)
 */
public interface SettingQueryService {

    List<SettingSummaryResponse> findAll();

    /**
     * @throws SettingNotFoundException 해당 id 의 정의서가 없을 때 (advice 404)
     */
    SettingDetailResponse findDetail(Long id);

    /** 펌웨어 업데이트 단계 폼의 보드/BIOS/BMC 선택지 — 제조사(Vendor) 그룹. */
    List<SettingBoardOptionGroupResponse> findBoardOptions();

    /** BASIC_SETTING 단계 폼의 BIOS 세팅 템플릿 선택지. */
    List<BiosTemplateOptionResponse> findBiosTemplateOptions();

    /** OS 설치/후처리 단계 폼의 OS·환경·패키지그룹 선택지 — OS 유형(OSName) 그룹. */
    List<SettingOSOptionGroupResponse> findOSOptions();

    /**
     * OS 별 권장 파티션 프리셋 — 현재 osName 무관 표준 프리셋.
     * OS 계열별 분기는 OS-다형 동작이 실구현되는 U2-4 의 책임.
     */
    List<PartitionPresetResponse> findDefaultPartitions(String osName);
}
