package com.example.serverprovision.domain.os.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FileUploadService {

    // 업로드 태스크 상태
    public enum UploadStatus { PROCESSING, COMPLETE, FAILED }

    // 개별 업로드 태스크 정보
    public record UploadTask(UploadStatus status, String savedPath, String errorMessage) {}

    // 태스크 ID → 상태 저장소 (in-memory, 서버 재시작 시 초기화)
    private final Map<String, UploadTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 업로드 태스크를 등록하고 비동기 파일 저장을 시작한다.
     *
     * @param tempPath  Spring이 저장한 임시 파일 경로
     * @param destPath  관리자가 지정한 최종 저장 절대 경로 (파일명 포함)
     * @return 생성된 taskId
     */
    public String startUpload(Path tempPath, String destPath) {
        validateDestPath(destPath);
        String taskId = UUID.randomUUID().toString();
        taskStore.put(taskId, new UploadTask(UploadStatus.PROCESSING, null, null));
        log.info("[FileUploadService] 업로드 태스크 생성. taskId={}, destPath={}", taskId, destPath);
        saveAsync(taskId, tempPath, Paths.get(destPath));
        return taskId;
    }

    /**
     * 비동기로 파일을 지정된 경로로 이동한다.
     * 중간 디렉토리가 없으면 자동으로 생성한다.
     */
    @Async
    public void saveAsync(String taskId, Path tempPath, Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            Files.move(tempPath, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FileUploadService] 저장 완료. taskId={}, path={}", taskId, dest);
            taskStore.put(taskId, new UploadTask(UploadStatus.COMPLETE, dest.toString(), null));
        } catch (Exception e) {
            log.error("[FileUploadService] 저장 실패. taskId={}, error={}", taskId, e.getMessage());
            taskStore.put(taskId, new UploadTask(UploadStatus.FAILED, null, e.getMessage()));
            tryDelete(tempPath);
        }
    }

    public UploadTask getTask(String taskId) {
        return taskStore.get(taskId);
    }

    // 경로 탐색 공격 방지: 절대 경로여야 하고 ".." 포함 불가
    private void validateDestPath(String destPath) {
        if (destPath == null || destPath.isBlank()) {
            throw new IllegalArgumentException("저장 경로를 입력해 주세요.");
        }
        Path p = Paths.get(destPath);
        if (!p.isAbsolute()) {
            throw new IllegalArgumentException("저장 경로는 절대 경로여야 합니다: " + destPath);
        }
        if (!p.normalize().equals(p)) {
            throw new IllegalArgumentException("저장 경로에 '..'를 사용할 수 없습니다: " + destPath);
        }
    }

    private void tryDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }
}
