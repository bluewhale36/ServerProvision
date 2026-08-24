package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.function.Function;

/**
 * 펌웨어 갱신 축(E2-2 D-1) — BIOS 와 BMC 는 같은 채널(Redfish SimpleUpdate)로 굽지만 <b>서로 다른
 * 자원이고 서로 다른 이유로 실패한다.</b> 축마다 달라지는 것을 상수가 자기 값으로 들어, 소비처는
 * {@code values()} 를 순회할 뿐 축 이름으로 분기하지 않는다.
 *
 * <p>이 형태를 택한 이유는 셋이다. ① 축이 늘 때 손대는 자리가 상수 하나다 — 실측된
 * {@code UpdateComponent} 허용값은 아홉 가지(BMC · BIOS · MB_CPLD · SCM_CPLD · BPB_CPLD · HPM_*)이고,
 * 분기로 짜면 빠뜨린 자리에서 새 축이 조용히 굽히지 않거나 남의 시한을 적용받는다. ② 축마다 다른
 * step · 컴포넌트명 · 인벤토리 경로 · 기본 시한이 한자리에 모여 드리프트가 없다. ③ 판정 쪽에서
 * {@link FirmwareAxisReason} 이 사유마다 등급과 문구를 든 것과 같은 계열이다 — 판정은 사유 enum 이,
 * 집행은 축 enum 이 데이터를 든다.</p>
 *
 * <p>기본 시한이 축마다 다른 것은 실측 소요가 네 배 가까이 벌어지기 때문이다(E0-4 — BIOS 47초~2분 22초,
 * BMC 7분 37초에 더해 완료 후 BMC 자기 재기동으로 5~10분 도달 불가). 하나로 덮으면 BIOS 쪽이 지나치게
 * 관대해져 벽돌을 늦게 발견한다. 설정으로 덮는 것은 {@link FlashTimeoutPolicy} 가 맡는다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum FirmwareAxis {

    BIOS(ProvisioningPhaseStep.BIOS_UPDATING, "BIOS", "BIOS", Duration.ofMinutes(15), FirmwareResolution::bios),
    BMC(ProvisioningPhaseStep.BMC_UPDATING, "BMC", "BMC", Duration.ofMinutes(30), FirmwareResolution::bmc);

    /** 이 축의 원장 step — 축마다 자기 행을 갖는 것이 축별 독립 성패(D-2)의 실체다. */
    private final ProvisioningPhaseStep step;

    /** SimpleUpdate 의 {@code UpdateComponent} 파라미터 값(E0-4-2 실측 허용값). */
    private final String updateComponent;

    /** {@code FirmwareInventory} 멤버 이름 — 반영 확인이 읽는 자리(E0-4-2 실측). */
    private final String inventoryMember;

    /** 집행 시한 기본값. 설정이 있으면 {@link FlashTimeoutPolicy} 가 덮는다. */
    private final Duration defaultTimeout;

    /** 이 축의 판정을 해석 결과에서 꺼내는 접근자 — 소비처가 bios/bmc 로 분기하지 않게 한다. */
    private final Function<FirmwareResolution, AxisResolution> accessor;

    /** 사용자 표시 라벨. BIOS · BMC 는 기술 표준 약어라 그대로 노출한다(new-user-copy 규칙 7). */
    public String label() {
        return updateComponent;
    }

    public AxisResolution resolutionOf(FirmwareResolution resolution) {
        return accessor.apply(resolution);
    }

    /** 이 step 이 어느 축의 것인가 — 커서에서 축을 되찾을 때 쓴다(실패 지점 판독 등). */
    public static FirmwareAxis of(ProvisioningPhaseStep step) {
        for (FirmwareAxis axis : values()) {
            if (axis.step == step) {
                return axis;
            }
        }
        return null;
    }
}
