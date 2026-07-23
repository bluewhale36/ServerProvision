package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetDashboardResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.common.view.IntegrityStatusView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 진단 리눅스 자산 6종의 현황·무결성 조회 + 무결성 봉인(baseline 기록). DB 를 쓰지 않는다 —
 * 슬롯은 고정 {@link DiagnosticAsset} enum 이고, 무결성 기준은 파일 옆 {@code .provision.json} 마커
 * 사이드카(디렉토리는 in-tree)가 SSOT 다(DEC-14 — 자원 도메인 아님, 엔티티 없음).
 *
 * <p><b>엔진 재사용</b>: 서명·해시 검증은 도메인 무관 {@link ProvisionMarkerService} 를 {@code Markable}
 * 엔티티 없이 임의 경로에 직접 호출한다(이 마커는 오직 이 서비스만 write/verify 하므로 봉인·검증이 같은
 * 해시 함수를 쓰면 정합이 보장된다 — 타 도메인 알고리즘과 일치시킬 필요가 없다). 디렉토리 트리 해시는
 * 기존 {@link BundleManifestService} 를 재사용한다.</p>
 *
 * <p><b>reconciliation 스윕 제외(격리)</b>: 이 마커들은 {@code pxe.assets.root} 아래에 있고, 그 루트는
 * reconciliation 스캔 루트/allowed-roots 에 포함되지 않는다 — 그래야 진단 자산 마커가 ORPHAN 으로
 * 오탐되지 않는다. 진단 자산은 이 화면의 자체 verify 만 갖는다.</p>
 *
 * <p><b>서빙 비활성 graceful</b>: 자산 서빙 설정({@code pxe.assets.root})이 없으면 {@link PxeAssetsProperties}
 * 빈 자체가 없다({@code @ConditionalOnProperty}). {@link ObjectProvider} 로 조회해 강결합을 피하고,
 * 미설정 환경에서도 대시보드는 오류 없이 "서빙 비활성" 상태로 조회된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticAssetIntegrityService {

    /** 마커 {@code resourceType} 문자열. reconciliation 스윕 대상이 아니므로 {@code ResourceType} enum 을 건드리지 않는다. */
    private static final String RESOURCE_TYPE = "DIAGNOSTIC_ASSET";

    private final ObjectProvider<PxeAssetsProperties> propertiesProvider;
    private final ProvisionMarkerService markerService;
    private final BundleManifestService bundleManifestService;
    private final FileSystemHardener fileSystemHardener;

    /**
     * 대시보드 조회. 서빙 활성 시 슬롯마다 존재·크기·수정시각·무결성을 산출한다. 조회는 부재·미봉인·
     * 서빙비활성을 전부 상태로 흡수하므로 절대 실패하지 않는다(예외로 거절하지 않는다).
     */
    public SystemAssetDashboardResponse loadDashboard() {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        boolean serving = props != null;

        List<SystemAssetSlotResponse> slots = new ArrayList<>();
        int ok = 0;
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            if (!serving) {
                slots.add(offSlot(asset));
                continue;
            }
            Probe probe = probe(asset, props.getRoot());
            IntegrityStatus status = probe.present() ? verify(asset, props.getRoot(), probe.hash()) : null;
            if (status == IntegrityStatus.ORIGINAL) {
                ok++;
            }
            slots.add(toResponse(asset, probe, status));
        }
        return new SystemAssetDashboardResponse(
                serving,
                serving ? props.getRoot().toString() : null,
                serving ? props.getBaseUrl() : null,
                slots, ok, DiagnosticAsset.values().length);
    }

    /**
     * 무결성 봉인 — 존재하는 슬롯의 현재 해시를 마커로 기록/갱신한다. <b>자산 파일은 건드리지 않는다</b>
     * (사이드카/인트리 마커만 쓴다). 봉인은 "현재 디스크 상태를 신뢰 기준으로 삼는" 행위이므로, 이미
     * 변조/손상된 자산을 봉인하면 그 상태가 기준선이 된다 — 컨트롤러/뷰가 확인 문구로 안내한다.
     */
    public SealResult seal() {
        Path root = requireServingRoot();
        int sealed = 0;
        int skipped = 0;
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            if (sealOne(asset, root)) {
                sealed++;
            } else {
                skipped++;
            }
        }
        log.info("[diagnostic-asset] 무결성 봉인 — 기록 {}건, 건너뜀(부재) {}건", sealed, skipped);
        return new SealResult(sealed, skipped);
    }

    /**
     * 서빙 활성 검증 후 자산 루트 반환. 봉인·교체처럼 자산 위치가 필요한 쓰기 액션의 공통 전제다 —
     * 서빙 비활성(빈 부재)이면 자산 위치를 알 수 없으므로 409 로 거절한다(UI 1차 차단의 안전망).
     */
    public Path requireServingRoot() {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            throw SystemAssetServingDisabledException.servingDisabled();
        }
        return props.getRoot();
    }

    /**
     * 한 슬롯의 현재 상태를 신뢰 기준으로 마커에 기록/갱신한다 — 존재하면 기록(true), 부재면 건너뜀(false).
     * 교체(E1-I-2-a)가 스왑 직후 자동 재봉인에 재사용한다(마커 조립 로직의 SSOT — 복붙 금지).
     */
    public boolean sealOne(DiagnosticAsset asset, Path root) {
        Probe probe = probe(asset, root);
        if (!probe.present()) {
            return false;
        }
        Path path = asset.resolve(root);
        MarkerContent content = new MarkerContent(
                RESOURCE_TYPE, (long) asset.ordinal(),
                Map.of("filename", asset.filename()),
                Instant.now(), probe.hash(), null);
        MarkerContent signed = content.withSignature(markerService.computeSignature(content));
        markerService.write(path, asset.layout(), signed);   // IO 실패 → MarkerWriteFailedException(500)
        fileSystemHardener.applyDefaultPermissionsForFile(markerService.resolveMarkerFile(path, asset.layout()));
        return true;
    }

    // ── 판정 (분기 아닌 우선순위 규약) ────────────────────────────────────────

    private IntegrityStatus verify(DiagnosticAsset asset, Path root, String recomputedHash) {
        Path path = asset.resolve(root);
        MarkerContent marker;
        try {
            marker = markerService.read(path, asset.layout());
        } catch (MarkerMissingException e) {
            return IntegrityStatus.MARKER_MISSING;                        // 기준선 미설정 → 봉인 유도
        } catch (MarkerWriteFailedException e) {
            log.warn("[diagnostic-asset] 마커 파싱 실패(손상) — SIGNATURE_INVALID 로 표시 : {}", path, e);
            return IntegrityStatus.SIGNATURE_INVALID;                     // 마커 자체가 읽히지 않으면 신뢰 불가
        }
        if (!markerService.verifySignature(marker)) {
            return IntegrityStatus.SIGNATURE_INVALID;                     // 마커 변조/키 불일치
        }
        if (!markerService.verifyManifestHash(marker, recomputedHash)) {
            return IntegrityStatus.TAMPERED;                              // 봉인 이후 자산 바이트가 바뀜
        }
        return IntegrityStatus.ORIGINAL;
    }

    // ── 자산 프로브 (존재·크기·수정시각·해시 1회 계산) ─────────────────────────

    private record Probe(boolean present, long sizeBytes, Instant modifiedAt, String hash) {
        static Probe absent() {
            return new Probe(false, 0L, null, null);
        }
    }

    private Probe probe(DiagnosticAsset asset, Path root) {
        Path path = asset.resolve(root);
        if (!asset.layout().resourceBodyExists(path)) {
            return Probe.absent();
        }
        try {
            Instant mtime = Files.getLastModifiedTime(path).toInstant();
            if (asset.layout() == MarkerLayout.IN_TREE) {
                BundleManifestService.ManifestSummary ms = bundleManifestService.compute(path);
                return new Probe(true, ms.totalBytes(), mtime, ms.manifestHash());
            }
            return new Probe(true, Files.size(path), mtime, sha256File(path));
        } catch (IOException e) {
            // 존재하는 자산의 읽기 실패는 진짜 IO 이상 — 500 이 정직하다.
            throw new IllegalStateException("진단 자산 조회 실패 : " + path, e);
        }
    }

    private static String sha256File(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, md)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) >= 0) {
                    // drain
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("자산 SHA-256 계산 실패 : " + file, e);
        }
    }

    // ── 응답 조립 (뷰는 문자열만 받는다) ──────────────────────────────────────

    private SystemAssetSlotResponse toResponse(DiagnosticAsset asset, Probe probe, IntegrityStatus status) {
        boolean present = probe.present();
        String statusLabel = present ? status.getDisplayMessage() : "자산 없음";
        String badge = present ? IntegrityStatusView.badgeClass(status) : "n-badge-gray";
        return new SystemAssetSlotResponse(
                asset.name(), asset.label(), asset.category().label(), asset.filename(), layoutLabel(asset.layout()),
                present, asset.replaceable(), present ? humanSize(probe.sizeBytes()) : "—", probe.modifiedAt(),
                statusLabel, badge, asset.replaceCadence());
    }

    private SystemAssetSlotResponse offSlot(DiagnosticAsset asset) {
        return new SystemAssetSlotResponse(
                asset.name(), asset.label(), asset.category().label(), asset.filename(), layoutLabel(asset.layout()),
                false, asset.replaceable(), "—", null, "서빙 비활성", "n-badge-gray", asset.replaceCadence());
    }

    private static String layoutLabel(MarkerLayout layout) {
        return layout == MarkerLayout.IN_TREE ? "디렉토리" : "단일 파일";
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int i = -1;
        do {
            value /= 1024;
            i++;
        } while (value >= 1024 && i < units.length - 1);
        return String.format("%.1f %s", value, units[i]);
    }
}
