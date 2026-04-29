package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.DirectoryMissingException;
import com.example.serverprovision.management.os.exception.InsufficientDiskSpaceException;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import com.example.serverprovision.management.os.exception.DuplicateFilenameException;
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import com.example.serverprovision.management.os.exception.OSImageNotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import com.example.serverprovision.management.os.util.IsoPathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ISO 업로드 Intent 핸드셰이크 관리자.
 *
 * <p>MK2 — intent 응답에 {@code preExistingMatch} 사전 경고 동봉 (단계 A). 같은 OS 의 동일 isoPath 로
 * 등록된 soft-deleted ISO 가 있으면 클라이언트가 안내 modal 1차 dismiss 후 업로드 진입한다.
 * 단계 B (해시 후) 의 nudge 흐름과는 독립 — 메타만 같고 파일이 달라도 본 사전 경고는 발생할 수 있다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsoUploadIntentService {

    private static final Duration TTL = Duration.ofHours(2);

    private final OSImageRepository osImageRepository;
    private final ISORepository isoRepository;

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    public IsoUploadIntentResponse issue(Long osImageId, IsoUploadIntentRequest request) {
        // 부모 OS 존재 확인
        osImageRepository.findByIdAndIsDeletedFalse(osImageId)
                .orElseThrow(() -> new OSImageNotFoundException(osImageId));

        String rawPath = request.isoPath();
        String filename = request.filename();
        long size = request.size();

        String resolvedPath = IsoPathResolver.resolve(
                rawPath,
                filename,
                p -> new IsoUploadIntentConflictException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + p));

        // 하드 블록 : 같은 OS 의 동일 경로에 활성 ISO 가 이미 등록됨 (DB 차원)
        Optional<ISO> existingSamePath = isoRepository
                .findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(osImageId, resolvedPath);
        if (existingSamePath.isPresent()) {
            throw new IsoUploadIntentConflictException(
                    "같은 경로에 이미 등록된 ISO 가 있습니다 : " + resolvedPath);
        }

        // 하드 블록 : 파일시스템 상 동일 이름 파일이 실재 — 덮어쓰기 방지
        try {
            Path target = Path.of(resolvedPath);
            if (Files.exists(target) && !Files.isDirectory(target)) {
                throw new DuplicateFilenameException(resolvedPath);
            }
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent) && !request.allowCreateDirectory()) {
                throw new DirectoryMissingException(parent.toString());
            }
            // 디스크 공간 — 부모 디렉토리가 실재할 때만 측정.
            if (size > 0 && parent != null && Files.exists(parent)) {
                try {
                    FileStore store = Files.getFileStore(parent);
                    long usable = store.getUsableSpace();
                    long safetyMargin = Math.max(size / 10, 256L * 1024 * 1024);
                    if (usable < size + safetyMargin) {
                        throw new InsufficientDiskSpaceException(resolvedPath, size + safetyMargin, usable);
                    }
                } catch (IOException ioe) {
                    log.warn("[IsoUploadIntentService] FileStore 조회 실패 — 디스크 공간 검증 건너뜀. parent={}, msg={}",
                            parent, ioe.getMessage());
                }
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new IsoUploadIntentConflictException("ISO 경로 형식이 올바르지 않습니다 : " + resolvedPath);
        }

        // 소프트 경고 수집
        List<String> warnings = new ArrayList<>();
        if (size == 0) {
            warnings.add("파일 크기가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
        }

        // MK2 — 단계 A 사전 경고. 같은 (osImageId, isoPath) 의 soft-deleted 후보가 있으면 동봉.
        PreExistingMatchInfo preExistingMatch = isoRepository
                .findFirstByOsImage_IdAndIsoPathAndIsDeletedTrue(osImageId, resolvedPath)
                .map(c -> new PreExistingMatchInfo(
                        c.getId(),
                        c.currentStage(),
                        c.getOsImage().getOsVersion(),
                        c.getIsoPath()))
                .orElse(null);

        String token = UUID.randomUUID().toString();
        intents.put(token, new Intent(osImageId, resolvedPath, filename, size, Instant.now()));
        log.info("[IsoUploadIntentService] intent 발급. token={}, osImageId={}, rawPath={}, resolvedPath={}, size={}, preExistingMatch={}",
                token, osImageId, rawPath, resolvedPath, size, preExistingMatch != null ? preExistingMatch.id() : null);
        return new IsoUploadIntentResponse(token, warnings, preExistingMatch);
    }

    public Intent consume(Long osImageId, String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로고침 후 다시 시도하세요.");
        }
        Intent intent = intents.remove(token);
        if (intent == null) {
            throw new InvalidUploadTokenException("만료되었거나 유효하지 않은 업로드 토큰입니다.");
        }
        if (!intent.osImageId().equals(osImageId)) {
            throw new InvalidUploadTokenException("토큰과 요청 OS 가 일치하지 않습니다.");
        }
        Duration age = Duration.between(intent.issuedAt(), Instant.now());
        if (age.compareTo(TTL) > 0) {
            throw new InvalidUploadTokenException("업로드 토큰이 만료되었습니다. 다시 시도해주세요.");
        }
        return intent;
    }

    @Scheduled(fixedDelayString = "${upload.intent.prune-interval-ms:300000}")
    public void prune() {
        Instant cutoff = Instant.now().minus(TTL);
        intents.entrySet().removeIf(e -> e.getValue().issuedAt().isBefore(cutoff));
    }

    public int size() { return intents.size(); }

    public record Intent(Long osImageId, String isoPath, String filename, long expectedSize, Instant issuedAt) {}
}
