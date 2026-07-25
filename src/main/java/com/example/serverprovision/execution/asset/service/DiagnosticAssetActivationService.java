package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceFailedException;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
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
 * 진단 리눅스 단일 파일 자산의 <b>활성화</b>(E1-I-2-b-2). 슬롯에 새 버전을 앉히는 두 트리거를 한 곳에 모은다 —
 * 업로드 교체({@link #replace})와 저장된 버전으로의 롤백({@link #rollback}). 둘의 차이는 <b>소스뿐</b>이고(업로드
 * 스트림 대 이력 파일), 공용 코어 {@link #activate} 가 현재 활성본 보관 → 원자적 스왑 → 재봉인을 한 번만 구현한다.
 * E1-I-2-a 의 {@code DiagnosticAssetReplaceService} 를 흡수했다(교체 스왑 로직이 여기로 이동).
 *
 * <p>자산은 {@code /api/pxe/v1/assets/**} 로 게스트에 실시간 서빙되므로, 208MB 급 파일을 직접 덮어쓰면 쓰는
 * 도중 부팅한 게스트가 잘린 파일을 받는다 — 임시 파일에 완성한 뒤 원자적 rename 으로 "옛 파일 아니면 새 파일"만
 * 보이게 한다.</p>
 *
 * <p><b>활성화 코어의 순서</b> = ① 소스를 temp 로 완성 → ② 현재 활성본 archive → ③ 원자적 스왑 → ④ 권한 →
 * ⑤ 재봉인. 소스를 <b>먼저</b> temp 로 실체화하므로, ②의 archive 가 유발하는 prune 이 하필 롤백 소스(같은 계열의
 * 오래된 버전 파일)를 지우더라도 안전하다. archive 가 실패하면 스왑 전에 중단된다 — 옛 버전을 보존하지 못한 채
 * 덮어쓰지 않는다.</p>
 *
 * <p>이 서비스는 진단 자산에 특화돼 있다. dhcpd/tftp 자산이 합류하는 E1-I-3 이후, root·category·재봉인·slot 해석만
 * 도메인 훅으로 남기고 골격을 global 로 추출하는 SPI 일반화를 <b>단계 신설 없는 리팩토링</b>으로 진행한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticAssetActivationService {

    /** 버전 이력 category — 진단 자산 계열 1개. enum 의 {@code Category}(대시보드 분류)와는 다른 개념. */
    private static final String HISTORY_CATEGORY = "DIAGNOSTIC";

    private final DiagnosticAssetIntegrityService integrityService;
    private final AssetHistoryService historyService;
    private final FileSystemHardener fileSystemHardener;

    /**
     * 슬롯 자산을 업로드 파일로 교체한다. 가드(도메인 invariant if-throw): 서빙 활성(409) → 교체 대상(409)
     * → 비어있지 않은 파일(400). 통과 시 공용 활성화 코어로 위임한다.
     */
    public void replace(DiagnosticAsset slot, MultipartFile file) {
        Path root = integrityService.requireServingRoot();      // 서빙 비활성 → 409
        requireReplaceable(slot);                               // apkovl · repo → 409
        if (file == null || file.isEmpty()) {
            throw DiagnosticAssetReplaceEmptyException.of(slot);   // 빈 업로드 → 400
        }
        try (InputStream source = file.getInputStream()) {
            activate(slot, root, source);
        } catch (IOException e) {
            throw new DiagnosticAssetReplaceFailedException("업로드 스트림 열기 실패 : " + slot.filename(), e);   // 500
        }
        log.info("[diagnostic-asset] 교체 완료 — slot={}", slot.name());
    }

    /**
     * 슬롯 자산을 이력의 특정 버전으로 되돌린다. 가드: 서빙 활성(409) → 교체 대상(409). 버전이 없거나 그 슬롯
     * 소속이 아니면 {@code openVersion} 이 404(다른 자산 버전을 되살리는 forging 차단). 현재 활성본은 활성화 코어가
     * archive 하므로 롤백 자체도 되돌릴 수 있고, 되살린 버전은 이력에서 제거해 "이력 = 현재 아닌 되돌릴 수 있는
     * 버전" 불변을 유지한다.
     */
    public void rollback(DiagnosticAsset slot, Long versionId) {
        Path root = integrityService.requireServingRoot();      // 서빙 비활성 → 409
        requireReplaceable(slot);                               // 비대상 → 409
        AssetVersionKey key = new AssetVersionKey(HISTORY_CATEGORY, slot.filename());
        Path versionPath = historyService.openVersion(key, versionId);   // 없음 / 타 키 → 404
        try (InputStream source = Files.newInputStream(versionPath)) {
            activate(slot, root, source);
        } catch (IOException e) {
            throw new DiagnosticAssetReplaceFailedException("버전 열기 실패 : " + versionPath, e);   // 500
        }
        historyService.remove(key, versionId);                 // 되살린 버전을 이력에서 제거(승격, 멱등)
        log.info("[diagnostic-asset] 롤백 완료 — slot={}, versionId={}", slot.name(), versionId);
    }

    /**
     * 공용 활성화 코어 — 주어진 바이트 소스를 슬롯의 활성 파일로 앉힌다. 순서는 클래스 Javadoc 참고.
     * archive 실패(500)·스왑 IO 실패(500) 시 temp 를 정리하고 활성 파일을 건드리지 않는다(fail-closed).
     */
    private void activate(DiagnosticAsset slot, Path root, InputStream source) {
        Path target = slot.resolve(root);
        // 임시 파일은 반드시 자산 루트 하위(= target 과 같은 파일시스템)여야 ATOMIC_MOVE 가 성립한다.
        Path temp = root.resolve(slot.filename() + ".activate-" + UUID.randomUUID() + ".tmp");
        AssetVersionKey key = new AssetVersionKey(HISTORY_CATEGORY, slot.filename());
        try {
            Files.copy(source, temp);                          // ① 소스를 temp 로 완성(archive-prune 전 실체화)
            historyService.archive(key, target);               // ② 현재 활성본 보관(실패 시 500 → 스왑 전 중단)
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);   // ③ 원자적 스왑
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new DiagnosticAssetReplaceFailedException("자산 활성화 스왑 실패 : " + target, e);   // 500
        } catch (RuntimeException e) {
            deleteQuietly(temp);                               // 아카이브 실패(500) 등 — temp 정리 후 전파
            throw e;
        }
        fileSystemHardener.applyDefaultPermissionsForFile(target);   // ④
        integrityService.sealOne(slot, root);                       // ⑤ 새 파일이 신뢰 기준 — 자동 재봉인
    }

    private static void requireReplaceable(DiagnosticAsset slot) {
        if (!slot.replaceable()) {
            throw DiagnosticAssetNotReplaceableException.of(slot);   // apkovl · repo → 409
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 임시 파일 정리 실패는 무해 — 다음 활성화가 새 UUID 를 쓴다.
        }
    }
}
