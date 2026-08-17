package com.example.serverprovision.provisioning.group.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.setting.dto.response.ReferencedDefinitionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;

import java.util.List;

/**
 * 그룹 상세의 '표준 정의서' 절 한 판 (U3-5-d).
 *
 * <p>표준이 없는 그룹과 있는 그룹을 <b>같은 타입</b>으로 다룬다. 화면이 먼저 null 인지 보고 다시 안쪽을
 * 보는 두 단계를 밟지 않게 하려는 것이다. 정하지 않은 상태({@link #none()})는 결함이 아니라 그룹의
 * 출발 상태다.</p>
 *
 * <p>해석 자체는 {@code setting} 이 한다({@link ReferencedDefinitionResponse}) — 이 응답은 그것을
 * 그룹 화면의 어휘로 옮길 뿐이다. 그룹 서비스가 정의서를 직접 조회하지 않고 컨트롤러가 두 쪽을 잇는
 * 형태는 U3-5-c 와 같다.</p>
 */
public record GroupStandardResponse(
        ReferencedDefinitionResponse reference,
        /**
         * 이 표준을 붙이면 밟게 될 프로비저닝 단계 — 선언 순 (U3-5-d 개정).
         *
         * <p>이름만으로는 그 정의서가 무엇을 하는지 알 수 없다. 운영자가 지은 이름이라 규칙이 없고,
         * 확인하려면 정의서 상세로 나가야 했다. 표준을 정하는 자리에서 바로 보이게 한다.</p>
         *
         * <p>계산은 {@code assignment} 가 한다 — 할당이 쓰는 것과 같은 매핑이어야 표시와 실제가
         * 어긋나지 않기 때문이다. 그룹 서비스가 직접 도출하지 않고 컨트롤러가 받아 넘긴다.
         * 표준이 없거나 정의서가 사라졌으면 빈 목록이다.</p>
         */
        List<ProvisioningPhase> phases
) {

    private static final GroupStandardResponse NONE = new GroupStandardResponse(null, List.of());

    /** 아직 표준을 정하지 않은 그룹. */
    public static GroupStandardResponse none() {
        return NONE;
    }

    public static GroupStandardResponse of(ReferencedDefinitionResponse reference,
                                           List<ProvisioningPhase> phases) {
        return new GroupStandardResponse(reference, phases);
    }

    /** 표준을 정해 두었는가 — 그 정의서를 <b>지금 쓸 수 있는지</b>는 따로 묻는다. */
    public boolean present() {
        return reference != null;
    }

    /** 정해 두었고 지금 붙일 수도 있는가 — 배너와 [표준 적용] 이 열리는 조건이다. */
    public boolean usable() {
        return present() && reference.usable();
    }

    /**
     * 가리키던 정의서가 아직 있는가 — <b>이름을 그 정의서 상세로 링크할 수 있는가</b>와 같은 물음이다.
     *
     * <p>{@link #usable()} 과 다르다. 비활성 · 삭제된 정의서는 붙일 수 없지만 <b>상세는 열린다</b>
     * (그 화면에서 복원 · 활성화를 하게 된다). 아예 사라진 정의서만 열 곳이 없다.</p>
     */
    public boolean resolved() {
        return present() && reference.resolved();
    }

    public Long definitionId() {
        return present() ? reference.definitionId() : null;
    }

    public String name() {
        return present() ? reference.name() : null;
    }

    /** 쓸 수 없으면 그 사유 — 화면이 그대로 적고, 표준 지정을 거절하는 서버 가드와 같은 문자열이다. */
    public String blockReason() {
        return present() ? reference.blockReason() : null;
    }

    public boolean deprecated() {
        return present() && reference.deprecated();
    }

    /** 배너 계산에 넘길 정의서 요약 — 지금 쓸 수 있을 때만 뜻이 있다. */
    public SettingSummaryResponse definition() {
        return present() ? reference.definition() : null;
    }
}
