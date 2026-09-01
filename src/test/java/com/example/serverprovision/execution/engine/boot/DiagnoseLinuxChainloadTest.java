package com.example.serverprovision.execution.engine.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3.5-1 — 공용 체인로드 빌더: 커널 인자 계약(agent.sh SSOT)과 실패 폴백이 두 phase 공용이 됐다. */
class DiagnoseLinuxChainloadTest {

    @Test
    @DisplayName("커널 인자 — provision_token · provision_base · 절대 자산 URL · 실패 폴백(chain 재진입)")
    void script_contract() {
        String script = DiagnoseLinuxChainload.script("http://10.0.2.2:7777", "tok123", "systemUUID=abc");

        assertThat(script)
                .startsWith("#!ipxe")
                .contains("provision_token=tok123")
                .contains("provision_base=http://10.0.2.2:7777")
                .contains("kernel http://10.0.2.2:7777/api/pxe/v1/assets/vmlinuz-lts")
                .contains("apkovl=http://10.0.2.2:7777/api/pxe/v1/assets/diag.apkovl.tar.gz")
                .contains(":failed")
                .contains("chain /api/pxe/v1/boot?systemUUID=abc");
    }
}
