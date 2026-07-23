package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceFailedException;
import com.example.serverprovision.global.security.FileSystemHardener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 진단 리눅스 단일 파일 자산의 교체(E1-I-2-a). 업로드 파일을 <b>원자적 스왑</b>으로 갈아끼우고 그 슬롯을
 * 자동 재봉인한다. 자산은 {@code /api/pxe/v1/assets/**} 로 게스트에 실시간 서빙되므로, 208MB 급 modloop 을
 * 직접 덮어쓰면 쓰는 도중 부팅한 게스트가 잘린 파일(torn read)을 받는다 — 임시 파일에 완성한 뒤 원자적
 * rename 으로 "옛 파일 아니면 새 파일"만 보이게 한다.
 *
 * <p>순서 = {@code 스왑 → sealOne}(재봉인). E1-I-2-b-1 이 맨 앞에 {@code archive(현재→버전 저장소)}를 끼우면
 * {@code archive → 스왑 → sealOne} 이 되도록 남긴 확장 seam 이다 — 이 단계(a)는 archive 를 구현하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticAssetReplaceService {

    private final DiagnosticAssetIntegrityService integrityService;
    private final FileSystemHardener fileSystemHardener;

    /**
     * 슬롯 자산을 업로드 파일로 교체한다. 가드 순서(도메인 invariant if-throw): 서빙 활성(409) → 교체 대상(409)
     * → 비어있지 않은 파일(400). 통과 시 원자적 스왑 후 자동 재봉인.
     */
    public void replace(DiagnosticAsset slot, MultipartFile file) {
        Path root = integrityService.requireServingRoot();      // 서빙 비활성 → 409
        if (!slot.replaceable()) {
            throw DiagnosticAssetNotReplaceableException.of(slot);   // apkovl · repo → 409
        }
        if (file == null || file.isEmpty()) {
            throw DiagnosticAssetReplaceEmptyException.of(slot);     // 빈 업로드 → 400
        }

        Path target = slot.resolve(root);
        // 임시 파일은 반드시 자산 루트 하위(= target 과 같은 파일시스템)여야 ATOMIC_MOVE 가 성립한다.
        Path temp = root.resolve(slot.filename() + ".replace-" + UUID.randomUUID() + ".tmp");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, temp);                                   // 임시 파일에 완성
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);   // 원자적 교체
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new DiagnosticAssetReplaceFailedException("자산 교체 스왑 실패 : " + target, e);   // → 500
        }
        fileSystemHardener.applyDefaultPermissionsForFile(target);
        integrityService.sealOne(slot, root);                       // 새 파일이 신뢰 기준 — 자동 재봉인
        log.info("[diagnostic-asset] 교체 완료 — slot={}, target={}", slot.name(), target);
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 임시 파일 정리 실패는 무해 — 다음 교체가 새 UUID 를 쓴다.
        }
    }
}
