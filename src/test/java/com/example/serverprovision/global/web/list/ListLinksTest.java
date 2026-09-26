package com.example.serverprovision.global.web.list;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 쪽 이동 · 초기화 링크 — page 만 바꾸고 나머지 상태는 그대로 든다(S8-1 R9). */
class ListLinksTest {

    private static ListLinks linksOf(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provisioning/setting");
        request.setQueryString(query);
        UriComponentsBuilder.fromUriString("/x?" + query).build().getQueryParams()
                .forEach((k, vs) -> vs.forEach(v -> request.addParameter(k,
                        java.net.URLDecoder.decode(v, StandardCharsets.UTF_8))));
        return ListLinks.of(request);
    }

    @Test
    @DisplayName("page(n) — 다른 파라미터(반복 값 포함)는 그대로 두고 page 만 새로 세운다")
    void page_keepsOtherParams() {
        ListLinks links = linksOf("enabled=false&processType=BASIC_UPDATE&processType=RAID_CONFIGURATION&page=3&sort=NAME");

        assertThat(links.page(1)).isEqualTo(
                "/provisioning/setting?enabled=false&processType=BASIC_UPDATE&processType=RAID_CONFIGURATION&sort=NAME&page=1");
    }

    @Test
    @DisplayName("page(0) — 첫 쪽은 기본값이라 URL 에 쓰지 않는다")
    void page_zero_omitsParam() {
        assertThat(linksOf("page=2").page(0)).isEqualTo("/provisioning/setting");
        assertThat(linksOf("enabled=true&page=2").page(0)).isEqualTo("/provisioning/setting?enabled=true");
    }

    @Test
    @DisplayName("reset() — 검색 · 필터 · 정렬 · 쪽을 모두 버린 경로만")
    void reset_returnsPathOnly() {
        assertThat(linksOf("q=abc&enabled=true&sort=NAME&page=2").reset()).isEqualTo("/provisioning/setting");
    }

    @Test
    @DisplayName("한글 · 특수 문자 검색어는 인코딩되어 실리고, 디코딩하면 원문으로 돌아온다")
    void page_encodesKoreanQuery() {
        String href = linksOf("q=" + java.net.URLEncoder.encode("8월 표준 & 50%", StandardCharsets.UTF_8)).page(1);

        String q = UriComponentsBuilder.fromUriString(href).build().getQueryParams().getFirst("q");
        assertThat(href).doesNotContain(" ").doesNotContain("월");
        assertThat(java.net.URLDecoder.decode(q, StandardCharsets.UTF_8)).isEqualTo("8월 표준 & 50%");
    }
}
