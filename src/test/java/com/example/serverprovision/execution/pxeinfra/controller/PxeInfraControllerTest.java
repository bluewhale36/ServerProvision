package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-3-b — dhcpd 임대 목록 페이지의 HTTP 계층 검증(구성 시나리오). {@link PxeInfraProperties} 빈이 존재하면
 * {@code configured} 가 true 가 되어 임대 표가 렌더된다. 조회 전용이라 상태를 바꾸는 액션·신규 예외가 없으므로
 * 4xx 시나리오는 없다. 미구성 안내는 {@code PxeInfraControllerUnconfiguredTest} 가 별도 컨텍스트로 지킨다.
 */
@WebMvcTest(controllers = PxeInfraController.class)
class PxeInfraControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DhcpLeaseReader leaseReader;
    // 빈이 존재하면 ObjectProvider 가 해소 → configured=true(구성 시나리오). 컨트롤러는 null 여부만 본다.
    @MockitoBean
    PxeInfraProperties pxeInfraProperties;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /system/pxe-infra — 200 + overview 모델 + 임대 표(활성 우선·집계·무만료 표기)")
    void overview_configured_rendersLeases() throws Exception {
        given(leaseReader.read()).willReturn(twoLeases());

        mvc.perform(get("/system/pxe-infra"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/pxe-infra/overview"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(content().string(allOf(
                        containsString("10.0.2.50"),
                        containsString("10.0.2.60"),
                        containsString("52:54:00:12:34:56"),
                        containsString("활성 1건"),
                        containsString("전체 2건"),
                        containsString("무만료"))));
    }

    @Test
    @DisplayName("GET /system/pxe-infra — 임대 없음 → 200 + '기록된 임대가 없습니다.' 안내")
    void overview_configured_noLeases() throws Exception {
        given(leaseReader.read()).willReturn(LeaseSnapshot.empty());

        mvc.perform(get("/system/pxe-infra"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/pxe-infra/overview"))
                .andExpect(content().string(containsString("기록된 임대가 없습니다.")));
    }

    /** 활성(무만료) 1건 + 만료 1건 — 컨트롤러가 활성 우선 정렬·집계·배지 매핑을 하는 입력. */
    private static LeaseSnapshot twoLeases() {
        LeaseEntry active = new LeaseEntry(
                IpAddressVO.of("10.0.2.50"), MacAddressVO.of("52:54:00:12:34:56"),
                Instant.parse("2026-07-28T01:00:00Z"), Instant.MAX, LeaseBindingState.ACTIVE);
        LeaseEntry expired = new LeaseEntry(
                IpAddressVO.of("10.0.2.60"), null,
                Instant.parse("2026-07-27T10:00:00Z"), Instant.parse("2026-07-27T22:00:00Z"),
                LeaseBindingState.EXPIRED);
        return new LeaseSnapshot(List.of(active, expired));
    }
}
