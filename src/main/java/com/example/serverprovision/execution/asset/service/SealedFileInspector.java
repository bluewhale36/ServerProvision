package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.util.FileDigest;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 파일 봉인(sidecar/in-tree {@code .provision.json} 마커) 기반 자산 슬롯의 프로브·판정·봉인 부품. 진단 자산
 * 서비스({@code DiagnosticAssetIntegrityService})의 private {@code probe}+{@code verify}+{@code sealOne} 로직을
 * 영역 무관 무상태 부품으로 추출한 것이다 — 자산 영역(SPI 구현)이 자기 슬롯을 이 부품에 위임해 판정·봉인을
 * 공유한다(판정 사다리·마커 조립의 SSOT — 복붙 금지).
 *
 * <p><b>판정 정합(불가침)</b>: 판정 사다리(MARKER_MISSING → SIGNATURE_INVALID → TAMPERED → ORIGINAL, 부재면
 * MISSING)와 그 순서는 원본 {@code verify} 와 100% 동일하다. {@link SealedFileCondition} 이 원본
 * {@code IntegrityStatus} 와 byte 동일한 label/badge 를 승계하므로 E1-I-1/2 진단 테스트가 회귀하지 않는다.</p>
 *
 * <p><b>서명 정합</b>: 봉인·검증이 같은 {@link ProvisionMarkerService} 해시 함수를 쓰므로 봉인 직후 검증은 항상
 * ORIGINAL 이다. IN_TREE(디렉토리)는 {@link BundleManifestService} 트리 해시, SIDECAR(단일 파일)는
 * {@link FileDigest#sha256} 를 재계산 근거로 쓴다(레이아웃별 프로브 규칙 SSOT).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SealedFileInspector {

    private final ProvisionMarkerService markerService;
    private final BundleManifestService bundleManifestService;
    private final FileSystemHardener fileSystemHardener;

    /**
     * 단일 파일(SIDECAR) 해시 캐시 — 대시보드 진입마다 208MB 급 진단 커널을 통째로 재해시하는 비용을 없앤다.
     * (mtime, size) 지문이 캐시와 같으면 저장된 해시를 재사용한다. 파일은 어떤 쓰기든 mtime 이 갱신되므로 이
     * 지문은 내용 변경을 놓치지 않는다(비-stale — 봉인·교체가 파일을 바꾸면 지문이 달라져 자동 재해시). 재검사
     * 버튼은 mtime 을 보존한 변조까지 다시 잡도록 {@link #invalidateHashCache} 로 캐시를 비운다.
     */
    private final Map<Path, SidecarDigest> sidecarHashCache = new ConcurrentHashMap<>();

    private record SidecarDigest(Instant mtime, long size, String hash) {}

    /** 재검사(강제 신선 재검증) 시 캐시를 비워 다음 프로브가 전량 재해시하게 한다 — mtime 보존 변조까지 다시 잡는다. */
    public void invalidateHashCache() {
        sidecarHashCache.clear();
    }

    /**
     * 자산 경로 하나의 현황·무결성을 프로브해 {@link AssetSlotStatus} 로 반환한다. 존재·크기·수정시각을 계산하고
     * 판정 사다리로 {@link SealedFileCondition} 을 결정한다. 부재·미봉인·손상을 전부 판정으로 흡수하므로 예외로
     * 거절하지 않는다(진짜 IO 이상만 500). 서빙 비활성은 자산 위치를 알 수 없어 이 부품 밖(영역 어댑터)에서
     * NOT_CONFIGURED 로 흡수한다 — 이 메서드는 경로가 주어진 경우만 다룬다.
     */
    public AssetSlotStatus inspect(Path assetPath, MarkerLayout layout) {
        Probe probe = probe(assetPath, layout);
        if (!probe.present()) {
            return AssetSlotStatus.notPresent(SealedFileCondition.MISSING);
        }
        SealedFileCondition condition = verify(assetPath, layout, probe.hash());
        return AssetSlotStatus.present(probe.sizeBytes(), probe.modifiedAt(), condition);
    }

    /**
     * 자산 경로 하나의 현재 상태를 신뢰 기준으로 마커에 기록/갱신한다 — 존재하면 기록(true), 부재면 건너뜀(false).
     * <b>자산 파일은 건드리지 않는다</b>(사이드카/인트리 마커만 쓴다). 영역 어댑터의 영역 봉인과 교체 직후 자동
     * 재봉인이 공유한다(마커 조립 로직 SSOT).
     *
     * @param assetPath    자산 본체 경로
     * @param layout       마커 레이아웃(IN_TREE/SIDECAR)
     * @param resourceType 마커 {@code resourceType} 문자열(영역별)
     * @param ordinal      마커 {@code resourceId} 로 기록할 슬롯 서수
     * @param filename     마커 {@code attributes.filename}
     */
    public boolean seal(Path assetPath, MarkerLayout layout, String resourceType, long ordinal, String filename) {
        Probe probe = probe(assetPath, layout);
        if (!probe.present()) {
            return false;
        }
        MarkerContent content = new MarkerContent(
                resourceType, ordinal,
                Map.of("filename", filename),
                Instant.now(), probe.hash(), null);
        MarkerContent signed = content.withSignature(markerService.computeSignature(content));
        markerService.write(assetPath, layout, signed);   // IO 실패 → MarkerWriteFailedException(500)
        fileSystemHardener.applyDefaultPermissionsForFile(markerService.resolveMarkerFile(assetPath, layout));
        return true;
    }

    // ── 판정 (분기 아닌 우선순위 규약) ────────────────────────────────────────

    private SealedFileCondition verify(Path path, MarkerLayout layout, String recomputedHash) {
        MarkerContent marker;
        try {
            marker = markerService.read(path, layout);
        } catch (MarkerMissingException e) {
            return SealedFileCondition.MARKER_MISSING;                     // 기준선 미설정 → 봉인 유도
        } catch (MarkerWriteFailedException e) {
            log.warn("[sealed-file] 마커 파싱 실패(손상) — SIGNATURE_INVALID 로 표시 : {}", path, e);
            return SealedFileCondition.SIGNATURE_INVALID;                  // 마커 자체가 읽히지 않으면 신뢰 불가
        }
        if (!markerService.verifySignature(marker)) {
            return SealedFileCondition.SIGNATURE_INVALID;                  // 마커 변조/키 불일치
        }
        if (!markerService.verifyManifestHash(marker, recomputedHash)) {
            return SealedFileCondition.TAMPERED;                           // 봉인 이후 자산 바이트가 바뀜
        }
        return SealedFileCondition.ORIGINAL;
    }

    // ── 자산 프로브 (존재·크기·수정시각·해시 1회 계산) ─────────────────────────

    private record Probe(boolean present, long sizeBytes, Instant modifiedAt, String hash) {
        static Probe absent() {
            return new Probe(false, 0L, null, null);
        }
    }

    private Probe probe(Path path, MarkerLayout layout) {
        if (!layout.resourceBodyExists(path)) {
            return Probe.absent();
        }
        try {
            Instant mtime = Files.getLastModifiedTime(path).toInstant();
            if (layout == MarkerLayout.IN_TREE) {
                // 디렉토리 번들은 in-place 수정 시 디렉토리 mtime 이 안 바뀌어 (mtime,size) 지문이 안전하지 않다 —
                // 매니페스트 해시를 매번 계산한다(REPO 는 소형이라 커널만큼 무겁지 않다). 필요 시 stat 지문 캐시로 확장.
                BundleManifestService.ManifestSummary ms = bundleManifestService.compute(path);
                return new Probe(true, ms.totalBytes(), mtime, ms.manifestHash());
            }
            long size = Files.size(path);
            return new Probe(true, size, mtime, cachedSidecarHash(path, mtime, size));
        } catch (IOException e) {
            // 존재하는 자산의 읽기 실패는 진짜 IO 이상 — 500 이 정직하다.
            throw new IllegalStateException("자산 조회 실패 : " + path, e);
        }
    }

    /** (mtime,size) 지문이 캐시와 일치하면 저장된 해시를 재사용하고, 다르면 재계산해 캐시를 갱신한다(비-stale). */
    private String cachedSidecarHash(Path path, Instant mtime, long size) throws IOException {
        SidecarDigest cached = sidecarHashCache.get(path);
        if (cached != null && cached.mtime().equals(mtime) && cached.size() == size) {
            return cached.hash();
        }
        String hash = FileDigest.sha256(path);
        sidecarHashCache.put(path, new SidecarDigest(mtime, size, hash));
        return hash;
    }
}
