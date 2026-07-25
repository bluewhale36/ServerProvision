package com.example.serverprovision.global.history.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * E1-I-2-b-2 — 버전 이력 운영 설정. 단일행(singleton) 엔티티로, 자산별 보존 개수 하나만 담는다.
 *
 * <p>{@code TrashSettings} 의 singleton + 시드 + 캐시 패턴을 필드 하나로 축소해 미러링한다. 최초 시드 기본값은
 * {@code provision.asset.history.retention-count}({@link com.example.serverprovision.global.history.AssetHistoryProperties})
 * 에서 가져오므로, property 는 기본값의 출처로 남고 이 행이 운영자 오버라이드의 진실이 된다({@code count()==1} 기준
 * — {@link com.example.serverprovision.global.history.AssetHistorySettingsService} 가 시작 시 시드/정리).</p>
 *
 * <p>{@code retentionCount} 는 이력 전체 공통값이다(카테고리별 분리는 하지 않는다) — 필요해지는 시점에 분리한다.</p>
 */
@Entity
@Table(name = "asset_history_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetHistorySettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retention_count", nullable = false)
    private int retentionCount;

    private AssetHistorySettings(int retentionCount) {
        this.retentionCount = retentionCount;
    }

    /** 초기 시드용. property 기본값을 받아 첫 행을 만든다({@code count()==0} 일 때 서비스가 호출). */
    public static AssetHistorySettings seed(int retentionCount) {
        return new AssetHistorySettings(retentionCount);
    }

    /** 운영자 갱신 — 보존 개수 교체. */
    public void apply(int retentionCount) {
        this.retentionCount = retentionCount;
    }
}
