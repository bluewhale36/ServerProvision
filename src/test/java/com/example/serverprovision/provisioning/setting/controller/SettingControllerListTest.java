package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SettingSortField;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * S8-1 — 세팅 정의서 목록의 조회 조건 바인딩 · 쪽 정정 · 400 · 화면 조립을 HTTP 계층에서 본다.
 * Mocking 은 {@code SettingQueryService} 까지 — 바인딩 · Pageable 리졸버 · ListLinks 리졸버 · 템플릿은 실제로 돈다.
 */
@WebMvcTest(controllers = SettingController.class)
class SettingControllerListTest {

    @Autowired MockMvc mvc;
    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static SettingSummaryResponse summary(long id) {
        return new SettingSummaryResponse(id, "정의서-" + id, List.of(SettingProcessType.BASIC_UPDATE),
                false, true, false, LocalDateTime.now());
    }

    @BeforeEach
    void stub() {
        given(queryService.search(any(), any())).willAnswer(inv -> {
            Pageable p = inv.getArgument(1);
            return new PageImpl<>(List.of(summary(1L)), p, 45);
        });
    }

    private SettingListQuery capturedQuery() {
        ArgumentCaptor<SettingListQuery> captor = ArgumentCaptor.forClass(SettingListQuery.class);
        then(queryService).should().search(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(queryService).should().search(any(), captor.capture());
        return captor.getValue();
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("200 — 조건 없이 들어오면 기본 조회(번호 오름차순 · 20 개 · 필터 없음)와 쪽 이동 줄 · 조회 띠를 그린다")
    void defaultList() throws Exception {
        mvc.perform(get("/provisioning/setting"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/setting-list"))
                .andExpect(content().string(containsString("id=\"listQuery\"")))
                .andExpect(content().string(containsString("n-pagination")))
                .andExpect(content().string(containsString("href=\"/provisioning/setting?page=1\"")))
                .andExpect(content().string(not(containsString("setting-list.js"))));

        SettingListQuery q = capturedQuery();
        assertThat(q.sort()).isEqualTo(SettingSortField.ID);
        assertThat(q.dir()).isEqualTo(Sort.Direction.ASC);
        assertThat(q.isFiltered()).isFalse();
        assertThat(capturedPageable().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("200 — 모든 축이 바인딩된다: 검색어 · 활성 · 사용 중단 · 반복 단계 · 삭제 포함 · 정렬 항목 · 방향 · 쪽")
    void bindsAllAxes() throws Exception {
        mvc.perform(get("/provisioning/setting")
                        .param("q", "표준").param("enabled", "false").param("deprecated", "true")
                        .param("processType", "RAID_CONFIGURATION").param("processType", "OS_INSTALLATION")
                        .param("includeDeleted", "true").param("sort", "NAME").param("dir", "DESC")
                        .param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                // 조회 띠가 받은 상태를 되돌려 그린다 — 체크된 단계 칩 · 선택된 정렬
                .andExpect(content().string(containsString("value=\"RAID_CONFIGURATION\" checked=\"checked\"")))
                .andExpect(content().string(containsString("selected=\"selected\">이름</option>")))
                .andExpect(content().string(containsString("<option value=\"DESC\" selected=\"selected\">내림차순")));

        SettingListQuery q = capturedQuery();
        assertThat(q.q()).isEqualTo("표준");
        assertThat(q.enabled()).isFalse();
        assertThat(q.deprecated()).isTrue();
        assertThat(q.processTypes()).containsExactly(SettingProcessType.RAID_CONFIGURATION, SettingProcessType.OS_INSTALLATION);
        assertThat(q.includeDeleted()).isTrue();
        assertThat(q.sort()).isEqualTo(SettingSortField.NAME);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        Pageable p = capturedPageable();
        assertThat(p.getPageNumber()).isEqualTo(1);
        assertThat(p.getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("200 — '전체' 칩의 빈 값은 필터 없음(null)으로, 빈 단계 값은 무시된다")
    void emptyValuesMeanNoFilter() throws Exception {
        mvc.perform(get("/provisioning/setting").param("enabled", "").param("deprecated", "").param("processType", ""))
                .andExpect(status().isOk());

        SettingListQuery q = capturedQuery();
        assertThat(q.enabled()).isNull();
        assertThat(q.deprecated()).isNull();
        assertThat(q.processTypes()).isEmpty();
    }

    @Test
    @DisplayName("200 — 표시 개수 상한 100 · 음수 쪽은 첫 쪽으로 정정된다")
    void pageableIsClamped() throws Exception {
        mvc.perform(get("/provisioning/setting").param("size", "9999").param("page", "-1"))
                .andExpect(status().isOk());

        Pageable p = capturedPageable();
        assertThat(p.getPageSize()).isEqualTo(100);
        assertThat(p.getPageNumber()).isZero();
    }

    @Test
    @DisplayName("200 — 조건으로 비었으면 '조건에 맞는 … 없습니다' 와 초기화 링크")
    void emptyFilteredResult() throws Exception {
        willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)).given(queryService).search(any(), any());

        mvc.perform(get("/provisioning/setting").param("q", "없는이름"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 세팅 정의서가 없습니다.")))
                .andExpect(content().string(containsString("href=\"/provisioning/setting\"")))
                .andExpect(content().string(not(containsString("n-pagination"))));
    }

    @Test
    @DisplayName("200 — 범위 밖 쪽이면 '이 페이지에는 항목이 없습니다' 와 첫 페이지 링크")
    void outOfRangePage() throws Exception {
        willReturn(new PageImpl<>(List.of(), PageRequest.of(9, 20), 45)).given(queryService).search(any(), any());

        mvc.perform(get("/provisioning/setting").param("page", "9").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 페이지에는 항목이 없습니다.")))
                .andExpect(content().string(containsString("href=\"/provisioning/setting?enabled=true\"")));
    }

    // ==== 400 — 화이트리스트 밖 값은 바인딩 실패 ========================

    @Test
    @DisplayName("400 — 정렬 항목이 화이트리스트 밖(원시 속성명)이면 조회하지 않는다")
    void unknownSortField_400() throws Exception {
        mvc.perform(get("/provisioning/setting").param("sort", "guestToken")).andExpect(status().isBadRequest());
        then(queryService).should(never()).search(any(), any());
    }

    @Test
    @DisplayName("400 — 정렬 방향 · 단계 · 활성 값이 잘못되면 조회하지 않는다")
    void invalidEnumOrBoolean_400() throws Exception {
        mvc.perform(get("/provisioning/setting").param("dir", "UP")).andExpect(status().isBadRequest());
        mvc.perform(get("/provisioning/setting").param("processType", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/provisioning/setting").param("enabled", "maybe")).andExpect(status().isBadRequest());
        then(queryService).should(never()).search(any(), any());
    }
}
