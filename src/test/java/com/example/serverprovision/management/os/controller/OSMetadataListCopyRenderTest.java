package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.os.dto.response.ISOResponse;
import com.example.serverprovision.management.os.dto.response.OSGroupResponse;
import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S22-4 — OS 버전 목록의 ISO 아코디언 문구(HTTP 계층). 무결성 상태 · 사용 중단 · 삭제 표지가 상수 원문 대신
 * 화면 이름으로 그려지고, 빈 값이 '-' 인지 본다. 서비스는 mock 이고 템플릿은 실제로 렌더된다.
 */
@WebMvcTest(controllers = {OSMetadataController.class})
class OSMetadataListCopyRenderTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataService osMetadataService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService osMetadataLifecycleService;

    @MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
    @MockitoBean com.example.serverprovision.global.trash.service.UserPurgeService userPurgeService;   // HF31 — 컨트롤러 의존성
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static ISOResponse iso(long id, String path, boolean deleted, boolean deprecated, boolean extracted,
                                   IntegrityStatus status, boolean parentBlocksRestore) {
        return new ISOResponse(id, path, "", !deleted, deleted, deprecated,
                LifecycleStage.of(deprecated, deleted), extracted, true, List.of(), 0, status,
                false, false, false, parentBlocksRestore, false, null);
    }

    @Test
    @DisplayName("GET /management/os — ISO 아코디언이 무결성 · 사용 중단 · 삭제를 화면 이름으로, 빈 값을 '-' 로 그린다")
    void isoAccordion_rendersScreenNames() throws Exception {
        OSMetadataResponse os = new OSMetadataResponse(
                3L, OSName.ROCKY_LINUX_9, "9.6", "", true, false, true, LifecycleStage.DEPRECATED,
                List.of(
                        iso(10L, "/mnt/iso/a.iso", false, true, true, IntegrityStatus.NOT_VERIFIED, false),
                        iso(11L, "/mnt/iso/b.iso", true, false, false, IntegrityStatus.ORIGINAL, true)),
                List.of(), List.of());
        given(osMetadataService.findAllGrouped(any()))
                .willReturn(List.of(OSGroupResponse.of(OSName.ROCKY_LINUX_9, List.of(os))));

        mvc.perform(get("/management/os?selectId=3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(IntegrityStatus.NOT_VERIFIED.getDisplayMessage())))
                .andExpect(content().string(not(containsString(">NOT_VERIFIED<"))))
                .andExpect(content().string(containsString("추출 완료")))
                .andExpect(content().string(containsString(">사용 중단<")))
                .andExpect(content().string(not(containsString(">Deprecated<"))))
                .andExpect(content().string(containsString("n-accordion-badge\">삭제</span>")))
                .andExpect(content().string(containsString("OS 버전을 먼저 복구해야 합니다.")));
    }

    @Test
    @DisplayName("E4-1-b-2 D-20 — 등록 뒤 버전 확인에 실패한 ISO 는 '등록 실패' 배지와 사유를 보인다(행은 남음)")
    void isoAccordion_rendersRegistrationFailure() throws Exception {
        String reason = "ISO 의 실제 버전(Rocky Linux 11.0)과 등록한 OS 버전(Rocky Linux 10.0)이 다릅니다. 맞는 OS 아래에 다시 등록해야 합니다.";
        ISOResponse failed = new ISOResponse(12L, "/mnt/iso/c.iso", "", true, false, false,
                LifecycleStage.of(false, false), false, true, List.of(), 0, IntegrityStatus.NOT_VERIFIED,
                false, false, false, false, false, reason);
        OSMetadataResponse os = new OSMetadataResponse(
                3L, OSName.ROCKY_LINUX_10, "10.0", "", true, false, false, LifecycleStage.of(false, false),
                List.of(failed), List.of(), List.of());
        given(osMetadataService.findAllGrouped(any()))
                .willReturn(List.of(OSGroupResponse.of(OSName.ROCKY_LINUX_10, List.of(os))));

        mvc.perform(get("/management/os?selectId=3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("n-badge-red\">등록 실패</span>")))
                .andExpect(content().string(containsString("n-badge-red n-accordion-badge\">등록 실패</span>")))   // 접힌 머리줄(CP5 O-r2-2)
                .andExpect(content().string(containsString("ISO 의 실제 버전(Rocky Linux 11.0)과 등록한 OS 버전(Rocky Linux 10.0)이 다릅니다.")));
    }

    @Test
    @DisplayName("S23-1 — C1 은 메이저까지 보이고, 미지원 상수 그룹은 '미지원' 배지 · [ISO 추가] 비활성 + 사유, 추출 불가 ISO 는 [추출] 이 없다")
    void catalog_rendersLabelsUnsupportedBadgeAndExtractGate() throws Exception {
        ISOResponse windowsIso = new ISOResponse(20L, "/mnt/iso/win.iso", "", true, false, false,
                LifecycleStage.of(false, false), false, false, List.of(), 0, IntegrityStatus.NOT_VERIFIED,
                false, false, false, false, false, null);
        OSMetadataResponse windows = new OSMetadataResponse(
                5L, OSName.WINDOWS_SERVER_2025, "2025", "", true, false, false, LifecycleStage.of(false, false),
                List.of(windowsIso), List.of(), List.of());
        OSMetadataResponse centos = new OSMetadataResponse(
                6L, OSName.CENTOS, "7.9", "", true, false, false, LifecycleStage.of(false, false),
                List.of(), List.of(), List.of());
        given(osMetadataService.findAllGrouped(any())).willReturn(List.of(
                OSGroupResponse.of(OSName.WINDOWS_SERVER_2025, List.of(windows)),
                OSGroupResponse.of(OSName.CENTOS, List.of(centos))));

        mvc.perform(get("/management/os?selectId=5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Windows Server 2025</span>")))
                .andExpect(content().string(containsString("n-badge n-badge-gray\">미지원</span>")))
                .andExpect(content().string(containsString("data-tooltip=\"지원하지 않는 OS 입니다.\"")))
                // R21-1 — 사유는 감싸개가 버튼 아래 글자로 보인다. 같은 문장의 곁 안내 줄은 두지 않는다(7절 표기 3)
                .andExpect(content().string(not(containsString("<span class=\"n-hint\">지원하지 않는 OS 입니다.</span>"))))
                .andExpect(content().string(not(containsString("n-btn n-btn-sm n-btn-extract"))))   // 유일한 ISO(Windows)에 [추출] 버튼이 없다
                // C2 [+ OS 버전 등록]: 지원 그룹은 그 OS 를 넘기고, 미지원 그룹은 막는다(링크가 없다)
                .andExpect(content().string(containsString("href=\"/management/os/new?osName=WINDOWS_SERVER_2025\"")))
                .andExpect(content().string(not(containsString("osName=CENTOS"))))
                .andExpect(content().string(containsString("class=\"n-btn n-btn-sm n-btn-secondary n-btn-block\" disabled>+ OS 버전 등록</button>")));
    }
}
