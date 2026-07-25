package com.example.serverprovision.global.history;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 버전 저장소 설정(E1-I-2-b-1) — 이력 루트와 자산별 보존 개수를 바인딩한다({@code TrashPolicy} 미러).
 *
 * <p>{@code provision.asset.history.root} 는 <b>비 web-served</b> 전용 루트다 — {@code pxe.assets.root} 아래도,
 * {@code provision.path.allowed-roots} 안도 아니어야 옛 버전이 게스트에 서빙되거나 reconciliation 스윕에
 * ORPHAN 으로 잡히지 않는다(plan §2-2 · §8).</p>
 */
@Component
@RequiredArgsConstructor
public class AssetHistoryProperties {

    @Value("${provision.asset.history.root:/opt/provisioning/.asset-history}")
    private String root;

    @Value("${provision.asset.history.retention-count:3}")
    private int retentionCount;

    /** 이력 루트 절대 경로. 부재 시 {@code archive} 가 자동 생성한다. */
    public Path getRoot() {
        return Path.of(root);
    }

    /** 자산별 보존 버전 상한(FIFO). 기본 3. */
    public int getRetentionCount() {
        return retentionCount;
    }
}
