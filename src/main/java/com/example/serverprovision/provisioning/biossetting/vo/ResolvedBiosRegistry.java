package com.example.serverprovision.provisioning.biossetting.vo;

import com.example.serverprovision.provisioning.biossetting.enums.BiosRegistrySource;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * "이 보드의 레지스트리" 로 해석된 결과(E3-3 R2) — 메뉴 트리(XML 결합본)와 출처 사실을 한 벌로 든다.
 *
 * @param targetVersion 굽기 목표 버전(순위 1위 활성 BoardBIOS) — 후보가 없으면 null
 */
public record ResolvedBiosRegistry(
        BiosSetupMenu menu,
        BiosRegistrySource source,
        String biosVersion,
        String targetVersion,
        LocalDateTime capturedAt,
        String sourceBmcIp
) {

    public Map<BiosAttributeName, BiosAttribute> registry() {
        return menu.registry();
    }

    /** 배지 문구 — 출처 상수가 만든다. */
    public String label() {
        return source.label(biosVersion, targetVersion, capturedAt, sourceBmcIp);
    }
}
