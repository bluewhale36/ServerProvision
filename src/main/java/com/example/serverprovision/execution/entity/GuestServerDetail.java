package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.board.entity.BoardModel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

/**
 * 게스트 서버 하드웨어 인벤토리 — 하드웨어가 보고한 사실. guest_server 와 1:1.
 *
 * <p>U1 §D2 : {@code vendor} 컬럼을 제거한다 — 항상 {@code boardModel.getVendor()} 로 도출되므로 중복 저장은
 * 드리프트원. 다회 갱신 주체(진단/검증)가 있어 {@code @Version} 낙관적 락은 유지한다(§D1).</p>
 *
 * <p>{@code hardwareSpec} / {@code softwareSpec} (JSON 컬럼)은 컬럼만 미리 둔다 — 실제 수집·적재와 앱측
 * sealed record 매핑은 진단 리눅스 수집 슬라이스의 책임이다. 등록(U1) 시점엔 항상 {@code null} (DIAGNOSE_LINUX
 * 단계에서 채워짐). 저장 형식은 기존 {@code provisioning_progress.phaseMeta} / {@code setup_step.statusMeta} 관례와
 * 동일하게 JSON 문자열이다.</p>
 */
@Entity
@Table(name = "guest_server_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class GuestServerDetail extends BaseTimeEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false, unique = true)
    private GuestServer guestServer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id", nullable = false)
    private BoardModel boardModel;

    @Column(name = "board_serial", length = 128, unique = true)
    private String boardSerial;

    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_stage", nullable = false, length = 32)
    private DiscoveryStage discoveryStage;

    /** 하드웨어 인벤토리 (CPU·메모리·디스크·NIC). 진단 수집 슬라이스가 적재 — 등록 시 {@code null}. */
    @Column(name = "hardware_spec", columnDefinition = "json")
    private String hardwareSpec;

    /** 펌웨어·소프트웨어 인벤토리 (최초 BIOS·BMC·옵션 펌웨어 버전). 진단 수집 슬라이스가 적재 — 등록 시 {@code null}. */
    @Column(name = "software_spec", columnDefinition = "json")
    private String softwareSpec;

    @Version
    private Long version;
}
