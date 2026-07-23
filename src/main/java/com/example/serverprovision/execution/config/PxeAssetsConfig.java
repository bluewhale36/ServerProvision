package com.example.serverprovision.execution.config;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 진단 리눅스 자산 정적 서빙(E1-1, plan Q1 채택안) — {@code /api/pxe/v1/assets/**} 를
 * {@code pxe.assets.root} 디렉토리에 매핑한다. 전용 스트리밍 컨트롤러 대신 Spring resource handler 를
 * 쓰는 이유: 208MB(modloop)급 정적 파일의 Range 요청 · 경로 이탈 가드({@code PathResourceResolver})를
 * framework 가 이미 제공한다 — 수제 IO 코드는 과잉이다.
 *
 * <p>격리망 실배포에서 외부 httpd 이관이 유리해지면 E1-I 에서 재판단 — URL 원천이
 * {@code pxe.server.base-url} 하나라 이관 비용은 설정 1줄이다.</p>
 *
 * <p><b>마커 서빙 제외(E1-I-2-a 동반)</b>: 무결성 봉인 시 {@code .provision.json} 마커가 자산 루트에
 * 생기는데 이 루트가 게스트에 서빙되므로, 내부 마커가 {@code /api/pxe/v1/assets/*.provision.json} 로
 * 노출되는 것을 {@link MarkerHiddenResourceResolver} 로 막는다(비밀 누출은 없으나 내부 마커를 게스트에 줄
 * 이유가 없다).</p>
 */
@Configuration
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class PxeAssetsConfig implements WebMvcConfigurer {

    private final PxeAssetsProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/pxe/v1/assets/**")
                .addResourceLocations(properties.getRoot().toUri().toString())
                .resourceChain(true)
                .addResolver(new MarkerHiddenResourceResolver());
    }

    /**
     * 무결성 마커({@code .provision.json} 사이드카 · 인트리)를 정적 서빙 대상에서 제외한다 — 마커 경로는
     * null 을 반환해 404 로 처리하고, 그 외 자산은 표준 {@link PathResourceResolver} 에 위임한다.
     */
    static final class MarkerHiddenResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (isMarker(resourcePath)) {
                return null;
            }
            return super.getResource(resourcePath, location);
        }

        /** 사이드카({@code <name>.provision.json})와 인트리({@code .provision.json}) 모두 이 접미사로 끝난다. */
        static boolean isMarker(String resourcePath) {
            return resourcePath != null && resourcePath.endsWith(ProvisionMarkerService.SIDECAR_SUFFIX);
        }
    }
}
