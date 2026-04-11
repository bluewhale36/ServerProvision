package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.os.service.FileUploadService;
import com.example.serverprovision.domain.os.service.FileUploadService.UploadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/admin/os/api/upload")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 파일 업로드 시작.
     * 관리자가 지정한 destPath 에 파일을 백그라운드로 저장하고 taskId를 반환한다.
     *
     * POST /pxe/v1/admin/os/api/upload
     *   multipart: file
     *   param:     destPath (서버 절대 경로, 파일명 포함. 예: /mnt/iso/rocky9.iso)
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("destPath") String destPath) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "파일이 비어 있습니다."));
        }

        // 요청 스코프 종료 후 Spring 임시 파일이 사라지기 전에 서버 관리 임시 경로로 이동
        Path tempPath = Files.createTempFile("upload_", "_" + file.getOriginalFilename());
        file.transferTo(tempPath);

        String taskId;
        try {
            taskId = fileUploadService.startUpload(tempPath, destPath);
        } catch (IllegalArgumentException e) {
            Files.deleteIfExists(tempPath);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        log.info("[FileUploadController] 업로드 시작. destPath={}, taskId={}", destPath, taskId);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    /**
     * 업로드 상태 조회.
     *
     * GET /pxe/v1/admin/os/api/upload/{taskId}
     * 응답: { status: "PROCESSING"|"COMPLETE"|"FAILED", savedPath: "...", errorMessage: "..." }
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String taskId) {
        UploadTask task = fileUploadService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "status",       task.status().name(),
                "savedPath",    task.savedPath()    != null ? task.savedPath()    : "",
                "errorMessage", task.errorMessage() != null ? task.errorMessage() : ""
        ));
    }
}
