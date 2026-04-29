package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 업로드 size / 갯수 / 트리 byte 한도 검증.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadLimitsPolicy {

    private final UploadSecurityProperties uploadSecurityProperties;
    private final FileSystemSecurityProperties fileSystemSecurityProperties;

    public void assertFileCount(MultipartFile[] files) {
        if (files == null) return;
        int max = uploadSecurityProperties.maxFolderFiles();
        if (files.length > max) {
            throw new UploadLimitExceededException("파일 갯수 " + files.length + " > 한도 " + max);
        }
    }

    public void assertSingleFileSize(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        long max = uploadSecurityProperties.maxFileSize().toBytes();
        if (file.getSize() > max) {
            throw new UploadLimitExceededException("단일 파일 크기 " + file.getSize() + " bytes > 한도 " + max);
        }
    }

    public void assertTreeBytes(Path treeRoot) {
        if (treeRoot == null) return;
        long max = uploadSecurityProperties.maxTreeBytes().toBytes();
        AtomicLong total = new AtomicLong();
        // S3.1 (A3) — maxDepth 인자 명시로 무한 재귀 방어. follow-links 옵션 부재 = symlink 미추적.
        try (Stream<Path> stream = Files.walk(treeRoot, fileSystemSecurityProperties.maxDepth())) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    total.addAndGet(Files.size(p));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new UploadLimitExceededException("트리 크기 측정 실패 : " + e.getMessage());
        }
        if (total.get() > max) {
            throw new UploadLimitExceededException("트리 byte 합 " + total.get() + " > 한도 " + max);
        }
    }
}
