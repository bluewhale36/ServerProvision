package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.provisioning.setting.dto.response.BiosTemplateOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.PartitionPresetResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.dto.response.TimezoneRegionResponse;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;

import java.util.List;

/**
 * 세팅 정의서 조회 계약 — 컨트롤러는 이 인터페이스만 본다.
 * (U2-1 스텁 → U2-3 에서 {@link JpaSettingQueryService} 실영속 구현으로 대체 완료)
 */
public interface SettingQueryService {

    /**
     * 정의서 목록. {@code includeDeleted=false} 면 활성 전용(기본), true 면 soft-deleted 포함(휴지통 토글,
     * U3-2-b DEC-F · os 선례).
     */
    List<SettingSummaryResponse> findAll(boolean includeDeleted);

    /**
     * 할당 가능한 정의서 목록(U3-2-b DEC-G) — 게스트 서버 상세의 할당 · 재할당 드롭다운 선택지.
     * 차단 판정은 도메인 SSOT {@code SettingDefinition.assignBlockReason()} 이라 서버 가드와 같은 기준이다
     * (삭제 · 비활성 제외). deprecated 는 차단이 아니므로 포함되며 응답 플래그로 경고만 표시한다.
     */
    List<SettingSummaryResponse> findAssignable();

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

    /** 타임존 선택지 — IANA 대륙별 도시 목록(JVM tzdb, 계열 무관 공통). */
    List<TimezoneRegionResponse> findTimezoneOptions();

    /** OS 별 권장 파티션 프리셋 — 계열 무관 고정(사용자 확정 2026-07-11: ext4 기본, 분기하지 않음). */
    List<PartitionPresetResponse> findDefaultPartitions(String osName);
}
