package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.config.PxeBootProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3.5-1 공용 체인로드 빌더 — S19-2 부터 자산 URL 5 곳은 PXE 부팅 credential 이 든 절대 URL, provision_base 는 credential 없음, 재진입은 받은 URL 그대로. */
class DiagnoseLinuxChainloadTest {

    @Test
    @DisplayName("커널 인자 — credential URL 5 곳(kernel · initrd · alpine_repo · modloop · apkovl) · provision_token · credential 없는 provision_base · 실패 폴백 chain")
    void script_contract() {
        PxeBootUrls urls = new PxeBootUrls("http://10.0.2.2:7777", new PxeBootProperties("pxe", "s3cret", ""));
        String script = DiagnoseLinuxChainload.script(urls, "tok123", "http://pxe:s3cret@10.0.2.2:7777/api/pxe/v1/boot?systemUUID=abc");

        String auth = "http://pxe:s3cret@10.0.2.2:7777/api/pxe/v1/assets";
        assertThat(script)
                .startsWith("#!ipxe")
                .contains("kernel " + auth + "/vmlinuz-lts")
                .contains("alpine_repo=" + auth + "/repo/main")
                .contains("modloop=" + auth + "/modloop-lts")
                .contains("apkovl=" + auth + "/diag.apkovl.tar.gz")
                .contains("initrd " + auth + "/initramfs-lts")
                .contains("provision_token=tok123")
                .contains("provision_base=http://10.0.2.2:7777 ")
                .doesNotContain("provision_base=http://pxe")
                .contains(":failed")
                .contains("chain http://pxe:s3cret@10.0.2.2:7777/api/pxe/v1/boot?systemUUID=abc");
        assertThat(script.split("pxe:s3cret@", -1)).hasSize(7);   // 자산 5 + 재진입 1
    }
}
