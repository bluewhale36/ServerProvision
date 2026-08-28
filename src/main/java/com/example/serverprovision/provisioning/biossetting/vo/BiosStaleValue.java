package com.example.serverprovision.provisioning.biossetting.vo;

import com.example.serverprovision.provisioning.biossetting.enums.BiosStaleKind;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

import java.util.List;

/** 레지스트리와 어긋난 저장값 1건(E3-3) — 상세 경고 행 · 할당 차단 사유 · 집행 전 검증 위반이 전부 이 값에서 나온다. */
public record BiosStaleValue(BiosAttributeName name, String storedRaw, BiosStaleKind kind, List<String> allowed) {

    public BiosStaleValue {
        allowed = List.copyOf(allowed);
    }

    public String message() {
        return kind.message(name.value(), storedRaw, allowed);
    }
}
