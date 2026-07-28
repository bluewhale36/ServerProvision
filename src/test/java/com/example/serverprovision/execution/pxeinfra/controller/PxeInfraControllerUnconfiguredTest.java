package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-3-b — dhcpd 임대 목록 페이지의 미구성 시나리오. {@link com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties}
 * 빈이 없으면({@code @ConditionalOnProperty} 미충족) {@code ObjectProvider} 가 null 을 돌려 {@code configured=false} 가
 * 되고, 뷰는 임대 표 대신 미구성 안내를 렌더한다 — 오류 없이 200 으로 조회됨을 확인한다.
 */
@WebMvcTest(controllers = PxeInfraController.class)
class PxeInfraControllerUnconfiguredTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DhcpLeaseReader leaseReader;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /system/pxe-infra — 미구성 → 200 + overview 모델 + 미구성 안내")
    void overview_unconfigured_showsHint() throws Exception {
        given(leaseReader.read()).willReturn(LeaseSnapshot.empty());

        mvc.perform(get("/system/pxe-infra"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/pxe-infra/overview"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(content().string(containsString("dhcpd 관측이 구성되지 않았습니다")));
    }
}
