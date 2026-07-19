package com.example.serverprovision.provisioning.setting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * 세팅 정의서 생성·수정 공용 요청. ({@code POST /provisioning/setting} · {@code PUT /provisioning/setting/{id}})
 *
 * <p>레거시는 {@code SettingCreateRequest} 를 PUT 에 재사용했으나 이름이 계약을 오도하므로
 * 생성·수정이 같은 스키마를 공유하는 현실을 그대로 드러내는 단일 타입으로 정정했다.
 * 스키마가 갈라지는 시점에 분리한다.</p>
 */
public record SettingSaveRequest(

        @NotBlank(message = "정의서 명칭은 필수 입력값입니다.")
        @Size(max = 128, message = "정의서 명칭은 128자 이하로 입력해주세요.")
        String name,

        /**
         * 프로비저닝 단계 목록. 각 항목은 {@link AbstractProcessRequest} 의 {@code "type"} 판별자로
         * 역직렬화되며, {@code @Valid} 로 concrete 타입의 제약이 재귀 적용된다.
         */
        @Valid
        @NotEmpty(message = "하나 이상의 프로비저닝 단계를 선택해야 합니다.")
        List<AbstractProcessRequest> processList
) {

    /**
     * OS 설치와 OS 후처리가 함께 있으면 같은 OS 를 대상으로 해야 한다(사용자 확정 2026-07-05).
     * UI 는 한쪽 선택 시 다른 쪽 select 를 같은 값으로 고정(1차 차단) — 여기는 direct POST 안전망.
     * 영속 조회가 필요 없는 요청-로컬 정합이라 계열 검사기(SPI)가 아닌 Layer A 소관이다.
     */
    @AssertTrue(message = "OS 설치와 OS 후처리 단계는 같은 OS 를 대상으로 해야 합니다.")
    public boolean isOsSelectionConsistent() {
        if (processList == null) {
            return true;
        }
        Long installOs = null;
        Long settingOs = null;
        for (AbstractProcessRequest process : processList) {
            if (process instanceof OSInstallationRequest install) {
                installOs = install.getOsMetadataId();
            } else if (process instanceof OSSettingRequest setting) {
                settingOs = setting.getOsMetadataId();
            }
        }
        return installOs == null || settingOs == null || installOs.equals(settingOs);
    }

    /**
     * BIOS 설정 단계의 자체 보드 selector 가 SPECIFIED 면 템플릿은 1개(사용자 확정 —
     * AUTO 만 보드당 N개). 판정 기준은 자체 selector (2026-07-07 개정 — BASIC_UPDATE 와의
     * 동일성은 {@link #isBoardSelectionConsistent()} 가 보장). 템플릿의 실제 보드 일치는
     * 영속 조회가 필요해 검사기(BasicSettingReferenceInspector) 담당.
     */
    @AssertTrue(message = "메인보드를 지정한 경우 BIOS 세팅 템플릿을 1개만 선택할 수 있습니다.")
    public boolean isBasicSettingTemplateCountConsistent() {
        if (processList == null) {
            return true;
        }
        for (AbstractProcessRequest process : processList) {
            if (process instanceof BasicSettingRequest basicSetting
                    && basicSetting.getBoardModel() != null
                    && !basicSetting.getBoardModel().isAuto()
                    && basicSetting.getBiosSettingTemplateIds().size() > 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * 펌웨어 업데이트와 BIOS 설정이 함께 있으면 두 보드 selector 는 동일해야 한다(사용자 확정
     * 2026-07-07 — UI 는 한쪽 선택 시 다른 쪽을 같은 값으로 고정, 여기는 direct POST 안전망).
     */
    @AssertTrue(message = "펌웨어 업데이트와 BIOS 설정 단계는 같은 메인보드 선택을 사용해야 합니다.")
    public boolean isBoardSelectionConsistent() {
        if (processList == null) {
            return true;
        }
        BoardModelSelectionRequest firmwareBoard = null;
        BoardModelSelectionRequest settingBoard = null;
        for (AbstractProcessRequest process : processList) {
            if (process instanceof BasicUpdateRequest firmware) {
                firmwareBoard = firmware.getBoardModel();
            } else if (process instanceof BasicSettingRequest basicSetting) {
                settingBoard = basicSetting.getBoardModel();
            }
        }
        if (firmwareBoard == null || settingBoard == null) {
            return true;
        }
        return firmwareBoard.isAuto() == settingBoard.isAuto()
                && (firmwareBoard.isAuto()
                    || java.util.Objects.equals(firmwareBoard.boardModelId(), settingBoard.boardModelId()));
    }

    /**
     * U2-3 D7 — 단계 타입당 최대 1개. UI("단계 추가"가 추가된 타입을 비활성)의 서버 선차단이며,
     * DB 의 {@code UNIQUE(definition_id, process_type)} 안전망이 500 으로 새지 않게 400 으로 받는다.
     */
    @AssertTrue(message = "같은 단계 타입은 정의서당 한 번만 추가할 수 있습니다.")
    public boolean isProcessTypeUnique() {
        if (processList == null) {
            return true; // @NotEmpty 위반이 이미 보고된다 — 중복 오류 방지.
        }
        long valid = processList.stream().filter(Objects::nonNull).count();
        long distinct = processList.stream().filter(Objects::nonNull)
                .map(AbstractProcessRequest::processType).distinct().count();
        return valid == distinct;
    }
}
