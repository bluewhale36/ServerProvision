package com.example.serverprovision.provisioning.biossetting.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.board.entity.BoardModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BMC 에서 채집한 BIOS 속성 레지스트리 전문(E3-3) — 키는 (보드, BIOS 버전). 같은 버전의 레지스트리는 불변이라
 * 버전당 한 행이며 재채집은 건너뛴다(D-9). 자료 파일과 달리 <b>어느 버전의 것인지</b>와 <b>어디서 언제 받았는지</b>를
 * 함께 든다 — 편집기의 출처 배지와 굽기 목표 버전 대조가 이 두 사실에 걸려 있다.
 */
@Entity
@Table(name = "bios_registry_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_bios_registry_snapshot_board_version",
                columnNames = {"board_model_id", "bios_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BiosRegistrySnapshot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id", nullable = false, updatable = false)
    private BoardModel boardModel;

    /** BMC 가 보고한 BIOS 버전 문자열(예: F44) — {@code BoardBIOS.version} 과 같은 표기라 그대로 대조한다. */
    @Column(name = "bios_version", nullable = false, length = 64, updatable = false)
    private String biosVersion;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    @Column(name = "source_bmc_ip", length = 15, updatable = false)
    private String sourceBmcIp;

    /** 채집 당시의 게스트 — 소프트 참조(회수 · 삭제와 무관하게 사실로 남는다). */
    @Column(name = "guest_server_id", updatable = false)
    private UUID guestServerId;

    @Column(name = "attribute_count", nullable = false, updatable = false)
    private int attributeCount;

    @Column(name = "registry_json", nullable = false, columnDefinition = "longtext", updatable = false)
    private String registryJson;

    @Builder
    private BiosRegistrySnapshot(BoardModel boardModel, String biosVersion, LocalDateTime capturedAt,
                                 String sourceBmcIp, UUID guestServerId, int attributeCount, String registryJson) {
        this.boardModel = boardModel;
        this.biosVersion = biosVersion;
        this.capturedAt = capturedAt;
        this.sourceBmcIp = sourceBmcIp;
        this.guestServerId = guestServerId;
        this.attributeCount = attributeCount;
        this.registryJson = registryJson;
    }
}
