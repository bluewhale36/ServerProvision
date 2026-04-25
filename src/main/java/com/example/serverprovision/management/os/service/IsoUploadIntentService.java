package com.example.serverprovision.management.os.service;

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
 * <ol>
 *   <li>{@code issue} : 업로드 전 사전 검증. isoPath 중복 등 하드 조건은 즉시 409 로 거절하여
 *       바이트 전송이 시작되지 않게 한다.</li>
 *   <li>{@code consume} : 업로드 요청의 {@code X-Upload-Token} 헤더를 검증하고 1회용 토큰을 소비한다.
 *       intent 단계와 실제 업로드의 (osId, isoPath) 가 어긋나면 거절.</li>
 * </ol>
 * 서버 최후 검증(SHA-256 체크섬) 은 그대로 {@link OSImageService#addISO} 에서 이어진다.
 * Intent 는 어디까지나 "명백한 낭비를 사전에 차단" 하는 방어선이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsoUploadIntentService {

    /** 토큰 유효 시간 — 업로드 자체가 길 수 있으므로 여유 있게. */
    private static final Duration TTL = Duration.ofHours(2);

    private final OSImageRepository osImageRepository;
    private final ISORepository isoRepository;

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    /**
     * Intent 발급. 하드 거절 조건에 해당하면 {@link IsoUploadIntentConflictException} 을 던진다.
     * 소프트 경고는 응답의 {@code warnings} 에 담아 클라이언트가 confirm 다이얼로그로 처리하도록 위임한다.
     */
    public IsoUploadIntentResponse issue(Long osImageId, IsoUploadIntentRequest request) {
        // 부모 OS 존재 확인
        osImageRepository.findByIdAndIsDeletedFalse(osImageId)
                .orElseThrow(() -> new OSImageNotFoundException(osImageId));

        String rawPath = request.isoPath();
        String filename = request.filename();
        long size = request.size();

        // 경로가 '/' 로 끝나면 디렉토리로 해석 → 업로드 파일명 append. 파일명 누락 시 409.
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
        // 디스크 공간 사전 검증 — 13GB 짜리 multipart 가 다 도착한 뒤 IOException 으로 실패하는 사고를 막는다.
        try {
            Path target = Path.of(resolvedPath);
            if (Files.exists(target) && !Files.isDirectory(target)) {
                throw new DuplicateFilenameException(resolvedPath);
            }
            // 하드 블록 : 상위 디렉토리가 없는데 "디렉토리 생성 허용" 체크도 안 된 경우
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent) && !request.allowCreateDirectory()) {
                throw new DirectoryMissingException(parent.toString());
            }
            // 디스크 공간 — 부모 디렉토리가 실재할 때만 측정 (생성 예정 디렉토리는 부모의 부모로 거슬러 올라가도 의미 단조롭지 않음)
            if (size > 0 && parent != null && Files.exists(parent)) {
                try {
                    FileStore store = Files.getFileStore(parent);
                    long usable = store.getUsableSpace();
                    // 10% 또는 256MB 의 안전 여유분을 두어 OS 의 reserve 영역과 충돌하지 않게.
                    long safetyMargin = Math.max(size / 10, 256L * 1024 * 1024);
                    if (usable < size + safetyMargin) {
                        throw new InsufficientDiskSpaceException(resolvedPath, size + safetyMargin, usable);
                    }
                } catch (IOException ioe) {
                    // FileStore 조회 실패는 차단 사유가 아님 — 기록만 하고 통과 (후단에서 IOException 재현됨)
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

        String token = UUID.randomUUID().toString();
        intents.put(token, new Intent(osImageId, resolvedPath, filename, size, Instant.now()));
        log.info("[IsoUploadIntentService] intent 발급. token={}, osImageId={}, rawPath={}, resolvedPath={}, size={}",
                token, osImageId, rawPath, resolvedPath, size);
        return new IsoUploadIntentResponse(token, warnings);
    }

    /**
     * 토큰 검증 및 소비. 유효하면 {@link Intent} 반환 후 저장소에서 제거 (1회용).
     * - 토큰 없음/만료/타 OS : {@link InvalidUploadTokenException}
     */
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

    /** 주기적으로 만료된 토큰을 청소한다. */
    @Scheduled(fixedDelayString = "${upload.intent.prune-interval-ms:300000}")
    public void prune() {
        Instant cutoff = Instant.now().minus(TTL);
        intents.entrySet().removeIf(e -> e.getValue().issuedAt().isBefore(cutoff));
    }

    /** 테스트·디버깅용. */
    public int size() { return intents.size(); }

    /**
     * 1회용 intent 레코드.
     */
    public record Intent(Long osImageId, String isoPath, String filename, long expectedSize, Instant issuedAt) {}
}
