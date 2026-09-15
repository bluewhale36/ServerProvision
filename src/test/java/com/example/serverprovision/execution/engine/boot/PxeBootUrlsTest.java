package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.config.PxeBootProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S19-2 D-4 — 게스트가 받는 URL 의 조립: userinfo 삽입 · 포트 유지 · provision_base 는 credential 없음 · base-url 없으면 상대 재진입. */
class PxeBootUrlsTest {

    private static final PxeBootProperties CONFIGURED = new PxeBootProperties("pxe", "s3cret", "");
    private static final PxeBootProperties UNSET = new PxeBootProperties("pxe", "", "");

    @Test
    @DisplayName("credential 설정 — 재진입 · 자산은 http://pxe:secret@host:port/… 절대 URL, provision_base 는 credential 없이")
    void configured_absoluteWithUserinfo() {
        PxeBootUrls urls = new PxeBootUrls("http://10.0.2.2:7838/", CONFIGURED);

        assertThat(urls.reentry("systemUUID=abc&macAddress=aa")).isEqualTo("http://pxe:s3cret@10.0.2.2:7838/api/pxe/v1/boot?systemUUID=abc&macAddress=aa");
        assertThat(urls.reentry(null)).isEqualTo("http://pxe:s3cret@10.0.2.2:7838/api/pxe/v1/boot");
        assertThat(urls.assetsBase()).isEqualTo("http://pxe:s3cret@10.0.2.2:7838/api/pxe/v1/assets");
        assertThat(urls.provisionBase()).isEqualTo("http://10.0.2.2:7838");
        assertThat(urls.toString()).doesNotContain("s3cret").contains("userinfo");
    }

    @Test
    @DisplayName("credential 미설정 + base-url 있음 — credential 없는 절대 URL(채널은 provider 가 닫는다)")
    void unset_absoluteWithoutUserinfo() {
        PxeBootUrls urls = new PxeBootUrls("https://pxe.example.com", UNSET);
        assertThat(urls.reentry("q=1")).isEqualTo("https://pxe.example.com/api/pxe/v1/boot?q=1");
        assertThat(urls.assetsBase()).isEqualTo("https://pxe.example.com/api/pxe/v1/assets");
    }

    @Test
    @DisplayName("base-url 없음(관리 전용) — 재진입은 종전 상대 경로, 자산 URL 은 만들 수 없다")
    void noBase_relativeReentryOnly() {
        PxeBootUrls urls = new PxeBootUrls("", CONFIGURED);
        assertThat(urls.reentry("systemUUID=abc")).isEqualTo("/api/pxe/v1/boot?systemUUID=abc");
        assertThat(urls.reentry("")).isEqualTo("/api/pxe/v1/boot");
        assertThatThrownBy(urls::assetsBase).isInstanceOf(IllegalStateException.class).hasMessageContaining("pxe.server.base-url");
        assertThatThrownBy(urls::provisionBase).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("base-url 이 URL 이 아니면 기동 실패")
    void malformedBase_failsFast() {
        assertThatThrownBy(() -> new PxeBootUrls("not a url", CONFIGURED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pxe.server.base-url");
    }
}
