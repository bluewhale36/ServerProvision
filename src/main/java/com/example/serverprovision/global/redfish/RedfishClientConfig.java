package com.example.serverprovision.global.redfish;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

/**
 * Redfish 전용 {@link RestClient} (E1.5 D1) — BMC 는 자가서명 인증서에 IP 접속이라(실측 전부 {@code curl -k})
 * 신뢰 저장소 검증과 호스트명 검증이 성립하지 않는다. 그 완화를 <b>이 클라이언트의 연결에만</b> 건다 —
 * comps.xml 추출용 전역 {@code RestClient} 빈과 분리하고, JVM 전역(시스템 프로퍼티 · 기본 HostnameVerifier)은
 * 건드리지 않는다({@link HttpsURLConnection} 연결 단위 hook).
 */
@Configuration
public class RedfishClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @Bean
    public RestClient redfishRestClient() {
        return RestClient.builder()
                .requestFactory(new InsecureTlsRequestFactory())
                .build();
    }

    /** 연결 단위 trust-all — {@code prepareConnection} 에서 해당 HTTPS 연결에만 완화를 적용한다. */
    static class InsecureTlsRequestFactory extends SimpleClientHttpRequestFactory {

        private final SSLContext trustAll;

        InsecureTlsRequestFactory() {
            setConnectTimeout(CONNECT_TIMEOUT_MS);
            setReadTimeout(READ_TIMEOUT_MS);
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, null);
                this.trustAll = context;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Redfish TLS 컨텍스트 초기화 실패", e);
            }
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            if (connection instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(trustAll.getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            }
        }
    }
}
