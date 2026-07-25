package com.example.serverprovision.global.history.entity;

import com.example.serverprovision.global.history.AssetVersionKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 아카이브된 파일 버전 1건(E1-I-2-b-1) — 이 자산 관리 영역의 첫 DB 엔티티. 교체 시 옛 파일을 이력 루트로
 * 복사하고 그 사실을 append-only 로 기록한다.
 *
 * <p><b>BaseTimeEntity 미상속</b>: 버전 로그는 삽입만 있고 수정이 없어 {@code updated_at} 이 무의미하다 —
 * {@link #archivedAt} 이 도메인 시각을 직접 표현한다({@code PurgeLog} 선례).</p>
 *
 * <p><b>{@code id}(IDENTITY) = 아카이브 순서</b>: 자동 증가 PK 가 삽입(=아카이브) 순서를 단조·유일하게
 * 담으므로 별도 seq 컬럼을 두지 않는다. 최신 순 조회는 {@code OrderByIdDesc}, FIFO 정리는 {@code OrderByIdAsc}.</p>
 *
 * <p><b>FK 없음</b>: {@code (category, name)} 은 자산 슬롯을 문자열로 가리킬 뿐 자산 테이블에 FK 를 걸지 않는다 —
 * 도메인 무관 저장소라 어떤 자산이든 키만 다르게 재사용한다.</p>
 */
@Entity
@Table(
        name = "asset_version",
        indexes = @Index(name = "idx_asset_version_key", columnList = "category, name, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 255)
    private String name;

    /** 이력 루트 기준 상대 경로. 예: {@code DIAGNOSTIC/modloop-lts/260724-010203-123_..._modloop-lts}. */
    @Column(name = "archived_rel_path", nullable = false, length = 512)
    private String archivedRelPath;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "archived_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant archivedAt;

    @Builder
    private AssetVersion(String category, String name, String archivedRelPath,
                         String sha256, long sizeBytes, Instant archivedAt) {
        this.category = category;
        this.name = name;
        this.archivedRelPath = archivedRelPath;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.archivedAt = archivedAt;
    }

    /** 아카이브 성공 시 한 버전 행을 합성. */
    public static AssetVersion of(AssetVersionKey key, String archivedRelPath,
                                  String sha256, long sizeBytes, Instant archivedAt) {
        return AssetVersion.builder()
                .category(key.category())
                .name(key.name())
                .archivedRelPath(archivedRelPath)
                .sha256(sha256)
                .sizeBytes(sizeBytes)
                .archivedAt(archivedAt)
                .build();
    }
}
