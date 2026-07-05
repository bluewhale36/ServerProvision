package com.example.serverprovision.management.common.filesystem.controller;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R8-2 — 통합 {@link DirectoryBrowseController}(/management/browse) 슬라이스 테스트.
 *
 * <p>구 도메인별 4 BrowseController 를 흡수한 단일 endpoint 의 계약을 못박는다 :
 * ① {@code includeFiles} param 전달(기본 false / 명시 true — subprogram-edit 의 파일 표시가 이 계약에 의존)
 * ② browse 예외 4종의 status·JSON 매핑이 통합 후에도 advice 채널(R2-3=R8-1)로 보존.</p>
 */
@WebMvcTest(controllers = DirectoryBrowseController.class)
class DirectoryBrowseControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ==== 성공 2xx — includeFiles param 계약 ==========================

    @Test
    @DisplayName("GET /management/browse — param 미지정 시 includeFiles=false 로 service 위임 + 200 JSON")
    void browse_default_excludesFiles() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/firmware", "/opt",
                        List.of(DirectoryListingResponse.Entry.directory("gigabyte"))));

        mvc.perform(get("/management/browse").param("path", "/opt/firmware"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/opt/firmware"))
                .andExpect(jsonPath("$.entries[0].type").value("DIR"))
                .andExpect(jsonPath("$.entries[0].name").value("gigabyte"));

        ArgumentCaptor<DirectoryBrowseRequest> captor = ArgumentCaptor.forClass(DirectoryBrowseRequest.class);
        then(directoryBrowseService).should().browse(captor.capture());
        assertThat(captor.getValue().path()).isEqualTo("/opt/firmware");
        assertThat(captor.getValue().includeFiles()).isFalse();
    }

    @Test
    @DisplayName("GET /management/browse?includeFiles=true — true 로 service 위임 (iso/subprogram-edit 파일 표시 계약)")
    void browse_includeFilesTrue_passesThrough() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/iso", "/opt",
                        List.of(DirectoryListingResponse.Entry.file("dvd.iso", 1024L))));

        mvc.perform(get("/management/browse")
                        .param("path", "/opt/iso")
                        .param("includeFiles", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].type").value("FILE"))
                .andExpect(jsonPath("$.entries[0].size").value(1024));

        ArgumentCaptor<DirectoryBrowseRequest> captor = ArgumentCaptor.forClass(DirectoryBrowseRequest.class);
        then(directoryBrowseService).should().browse(captor.capture());
        assertThat(captor.getValue().includeFiles()).isTrue();
    }

    // ==== browse 예외 4종 — advice status·JSON 채널 보존 (R8-1 라우팅) ====

    @Test
    @DisplayName("InvalidBrowsePath(@ResponseStatus 400) → 400 + JSON message")
    void browse_invalidPath_400() throws Exception {
        willThrow(new InvalidBrowsePathException("..%2f"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "..%2f")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("BrowseTargetNotFound(extends NotFoundException) → 404 + JSON message")
    void browse_notFound_404() throws Exception {
        willThrow(new BrowseTargetNotFoundException("/no/such/path"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/no/such/path")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("BrowseTargetNotDirectory(extends ConflictException) → 409 + JSON message")
    void browse_notDirectory_409() throws Exception {
        willThrow(new BrowseTargetNotDirectoryException("/opt/file.bin"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/opt/file.bin")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DirectoryBrowseIo(extends DomainException) → handleDomain 500 + JSON message")
    void browse_io_500() throws Exception {
        willThrow(new DirectoryBrowseIoException("디렉토리 열람 중 오류", new java.io.IOException("boom")))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/opt/x")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }
}
