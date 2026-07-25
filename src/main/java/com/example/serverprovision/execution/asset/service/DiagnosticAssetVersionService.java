package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.AssetVersionView;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.entity.AssetVersion;
import com.example.serverprovision.global.util.FileSize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 진단 자산 상세 화면의 버전 목록 조회(E1-I-2-b-2, 읽기 전용). {@link AssetHistoryService#list} 로 한 슬롯의
 * 보관 버전을 받아 {@link AssetVersionView} 로 미리 계산한다 — 활성 파일을 다시 해시하지 않고 오직
 * {@code asset_version} 행만 읽으므로 208MB 급 자산에도 저렴하다.
 *
 * <p>무결성 서비스({@code DiagnosticAssetIntegrityService})는 entity-free 로 유지하고, 이력 조회는 여기서
 * 분리해 컨트롤러가 둘을 합성한다.</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosticAssetVersionService {

    /** 버전 이력 category — {@code DiagnosticAssetActivationService.HISTORY_CATEGORY} 와 반드시 동일. */
    private static final String HISTORY_CATEGORY = "DIAGNOSTIC";
    private static final DateTimeFormatter ARCHIVED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AssetHistoryService historyService;

    /** 한 슬롯의 버전 목록(최신순). 상세 화면이 그 슬롯 이력만 조회한다 — 비대상 슬롯은 이력이 없어 빈 목록. */
    public List<AssetVersionView> listVersions(DiagnosticAsset slot) {
        return historyService
                .list(new AssetVersionKey(HISTORY_CATEGORY, slot.filename()))
                .stream()
                .map(DiagnosticAssetVersionService::toView)
                .toList();
    }

    private static AssetVersionView toView(AssetVersion version) {
        return new AssetVersionView(
                version.getId(),
                ARCHIVED_AT.format(version.getArchivedAt()),
                FileSize.format(version.getSizeBytes()),
                version.getSha256().substring(0, 12));
    }
}
