package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.VdAccessPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdBackgroundInit;
import com.example.serverprovision.provisioning.setting.enums.VdDriveCache;
import com.example.serverprovision.provisioning.setting.enums.VdInitialization;
import com.example.serverprovision.provisioning.setting.enums.VdIoPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdReadPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdStripSize;
import com.example.serverprovision.provisioning.setting.enums.VdWritePolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 묶음 규칙 한 행의 VD 파라미터 8축(E3.5-6) — MegaRAID "CONFIGURE VIRTUAL DRIVE PARAMETERS" 대응. 축마다 값이
 * 항상 있다 — 비운 축(null · 필드 누락)은 생성자가 각 enum 의 DEFAULT(9361-8i HII 기본 선택)로 채우고 집행에
 * 명시 전송한다(2026-09-02 미지정 축 폐지). record 전체가 null 인 규칙은 "이 묶음에 축이 없다"(IR 카드 · RAID 없음 ·
 * E3.5-6 이전 저장본)이고, MegaRAID 의 구 저장본은 계획({@code RaidPlanner})이 {@link #DEFAULTS} 로 본다.
 *
 * <p>storcli 조립의 SSOT — 폼 데모 · agent 전달 payload 가 이 세 메서드와 같은 진리표를 쓴다(plan D4):
 * {@link #createOpts()} 는 add vd 인라인, {@link #setOps()} 는 생성 후 per-VD set 인자, 초기화는
 * {@link #initToken()}. 중복 판정({@code DiskGroupRules.identity})에는 들어가지 않는다 — 다섯 축이
 * 같으면 파라미터가 달라도 같은 디스크 집합을 겹쳐 잡는 것이다(role 불포함과 같은 사유).</p>
 */
public record VdParameters(
        VdWritePolicy writePolicy,
        VdReadPolicy readPolicy,
        VdIoPolicy ioPolicy,
        VdStripSize stripSize,
        VdAccessPolicy accessPolicy,
        VdDriveCache driveCache,
        VdBackgroundInit backgroundInit,
        VdInitialization initialization
) {

    /** 8축 전부 HII 기본값 — 축을 실어 오지 않은 MegaRAID 규칙(구 저장본)의 조립 재료. */
    public static final VdParameters DEFAULTS = new VdParameters(null, null, null, null, null, null, null, null);

    public VdParameters {
        writePolicy = writePolicy == null ? VdWritePolicy.DEFAULT : writePolicy;
        readPolicy = readPolicy == null ? VdReadPolicy.DEFAULT : readPolicy;
        ioPolicy = ioPolicy == null ? VdIoPolicy.DEFAULT : ioPolicy;
        stripSize = stripSize == null ? VdStripSize.DEFAULT : stripSize;
        accessPolicy = accessPolicy == null ? VdAccessPolicy.DEFAULT : accessPolicy;
        driveCache = driveCache == null ? VdDriveCache.DEFAULT : driveCache;
        backgroundInit = backgroundInit == null ? VdBackgroundInit.DEFAULT : backgroundInit;
        initialization = initialization == null ? VdInitialization.DEFAULT : initialization;
    }

    /** Drive Cache 를 Unchanged 밖으로 골랐는가 — SSD 묶음 가드(규칙 9)의 재료. 폼은 SSD 행에서 이 축을 기본값으로 잠근다. */
    @JsonIgnore
    public boolean overridesDriveCache() {
        return driveCache != VdDriveCache.UNCHANGED;
    }

    /** add vd 인라인 옵션(HII 항목 중 생성 시 지정 계열) — 항상 5축 명시. */
    @JsonIgnore
    public String createOpts() {
        return String.join(" ", writePolicy.cliToken(), readPolicy.cliToken(), ioPolicy.cliToken(),
                stripSize.cliToken(), driveCache.cliToken());
    }

    /** 생성 후 per-VD {@code set} 인자 목록(BGI · Access Policy — add vd 인라인이 없는 계열) — 항상 2축. */
    @JsonIgnore
    public List<String> setOps() {
        return List.of(backgroundInit.cliToken(), accessPolicy.cliToken());
    }

    /** 초기화 방식 토큰("none"|"fast"|"full") — HII 기본은 none. */
    @JsonIgnore
    public String initToken() {
        return initialization.cliToken();
    }

    /** 상세 화면 한 줄 표기 — 8축 전부를 HII 항목 순서로 나열(집행에 실리는 값 그대로). */
    @JsonIgnore
    public String toDisplay() {
        return String.join(" · ",
                "Strip " + stripSize.getDisplayName(),
                readPolicy.getDisplayName(),
                writePolicy.getDisplayName(),
                ioPolicy.getDisplayName(),
                "Access " + accessPolicy.getDisplayName(),
                "Drive Cache " + driveCache.getDisplayName(),
                "Disable BGI " + backgroundInit.getDisplayName(),
                "Init " + initialization.getDisplayName());
    }
}
