package com.example.serverprovision.global.redfish;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Redfish 전용 {@link RestClient} (E1.5 D1) — BMC 는 자가서명 인증서에 IP 접속이라(실측 전부 {@code curl -k})
 * 신뢰 저장소 검증과 호스트명 검증이 성립하지 않는다. 그 완화를 <b>이 클라이언트의 연결에만</b> 건다 —
 * comps.xml 추출용 전역 {@code RestClient} 빈과 분리하고, JVM 전역(시스템 프로퍼티)은 건드리지 않는다.
 *
 * <p>E1.6 에서 {@code HttpURLConnection} 기반({@code SimpleClientHttpRequestFactory})을 JDK
 * {@link HttpClient} 기반으로 교체했다 — {@code HttpURLConnection} 은 <b>PATCH 메서드 자체를 지원하지
 * 않아</b> 계정 비밀번호 변경(If-Match PATCH)이 성립하지 않는다. 호스트명 검증 생략은
 * {@link X509ExtendedTrustManager}(no-op)로 이룬다 — 평범한 X509TrustManager 는 JDK 가
 * {@code AbstractTrustManagerWrapper} 로 감싸며 호스트명 검증을 추가 수행하므로(스모크 실증),
 * 확장형을 직접 구현해야 검증 주체가 이 no-op 이 된다.</p>
 */
@Configuration
public class RedfishClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient redfishRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .sslContext(trustAllContext())
                .sslParameters(new SSLParameters())   // endpoint identification 미지정 → 호스트명 검증 생략
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private static SSLContext trustAllContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) { }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Redfish TLS 컨텍스트 초기화 실패", e);
        }
    }
}
