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

    /**
     * 여러 정의서의 상세를 한 번에 (U3-5-b) — 정의서 선택 모달의 우측 패널을 전부 미리 렌더하는 데 쓴다.
     *
     * <p>{@link #findDetail(Long)} 을 목록만큼 반복 호출하지 않는 이유는 그 편이 호출 수만큼 트랜잭션을
     * 여는 형태이기 때문이다. 여기서는 한 트랜잭션 안에서 한 번에 읽는다.</p>
     *
     * <p><b>없는 id 는 예외 대신 결과에서 빠진다.</b> 호출자가 방금 받은 선택지에서 뽑은 id 를 넘기므로 빠진
     * 것은 그 사이에 삭제됐다는 뜻이고, 삭제된 정의서를 목록에서 빼는 것은 이미 정해진 규칙이다
     * (U3-2-b DEC-G). 사용자가 지정한 id 를 확인하는 자리가 아니라 화면 재료를 모으는 자리다.</p>
     */
    List<SettingDetailResponse> findDetailsOf(List<Long> ids);

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
