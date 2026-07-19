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

    /**
     * BMC 신원(E1-2 in-band 수집 · plan Q4) — hardwareSpec JSON 에 섞지 않고 구조화 컬럼으로 직행한다
     * (E3 에서 꺼내 옮기는 예정된 재작업 회피 — 로드맵 §3-E1-2 확정). E3-0 이 자격증명과 함께 별도
     * binding 엔티티로 승격할 여지가 있는 자리다. QEMU 등 BMC 미검출 환경은 null(정상 degrade).
     */
    @Convert(converter = com.example.serverprovision.execution.converter.IpAddressConverter.class)
    @Column(name = "bmc_ip", length = 15)
    private com.example.serverprovision.execution.vo.IpAddressVO bmcIp;

    @Convert(converter = com.example.serverprovision.execution.converter.MacAddressConverter.class)
    @Column(name = "bmc_mac", length = 17)
    private com.example.serverprovision.execution.vo.MacAddressVO bmcMac;

    @Version
    private Long version;

    /**
     * 진단 수집 적재(E1-2) — 하드웨어가 보고한 사실로 인벤토리를 채우고 수집 단계를
     * {@link DiscoveryStage#DIAGNOSTIC_ENRICHED} 로 승급한다. 재수집 보고는 최신값으로 덮는다
     * (관용 파서가 null 로 거른 필드는 기존 값을 지우지 않는다 — 부분 적재 보호).
     */
    public void enrich(String boardSerial, String hardwareSpec, String softwareSpec,
                       com.example.serverprovision.execution.vo.IpAddressVO bmcIp,
                       com.example.serverprovision.execution.vo.MacAddressVO bmcMac) {
        if (boardSerial != null) {
            this.boardSerial = boardSerial;
        }
        if (hardwareSpec != null) {
            this.hardwareSpec = hardwareSpec;
        }
        if (softwareSpec != null) {
            this.softwareSpec = softwareSpec;
        }
        if (bmcIp != null) {
            this.bmcIp = bmcIp;
        }
        if (bmcMac != null) {
            this.bmcMac = bmcMac;
        }
        this.discoveryStage = DiscoveryStage.DIAGNOSTIC_ENRICHED;
    }
}
