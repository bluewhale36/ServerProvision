package com.example.serverprovision.execution.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 앵커 — 정체성 + 운영자 입력 식별자 + 회수 마커.
 *
 * <p>U1 §D1 : 옛 {@code guest_server_custom} 을 흡수해 운영자 입력 4필드(name / modelName / serialNumber / memo)를
 * 단일 테이블에 둔다(인라인 수정의 write 시점·주체가 같음). §D3 : 옛 고아 {@code model_name} 컬럼은 이 운영자
 * 모델명으로 의미를 재정의한다. §D4 : 운영 상태는 저장하지 않고 (decommissionedAt + progress)에서 도출하므로
 * status 컬럼 없이 회수 시각만 둔다.</p>
 */
@Entity
@Table(name = "guest_server")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class GuestServer extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "system_uuid", nullable = false, unique = true)
    private UUID systemUUID;

    /** 운영자가 부여하는 식별 이름. 시스템 내 유일(미지정 가능 → nullable + UNIQUE 는 다중 null 허용). */
    @Column(name = "name", length = 128, unique = true)
    private String name;

    /** 사내 모델명 — 운영자 부여값. 진단 리눅스에서 {@code ipmitool} 로 하드웨어에 각인된다. (ex. RE2108) */
    @Column(name = "model_name", length = 32)
    private String modelName;

    /** 사내 시리얼 번호 — 운영자 부여값(하드웨어 보고값 {@code detail.boardSerial} 과 별개). (ex. RE210826510512) */
    @Column(name = "serial_number", length = 32, unique = true)
    private String serialNumber;

    @Column(name = "memo", length = 2000)
    private String memo;

    /** 회수(decommission) 시각. 미회수면 {@code null}. 운영 상태 도출의 유일한 비-progress 입력(§D4). */
    @Column(name = "decommissioned_at")
    private LocalDateTime decommissionedAt;

    /**
     * 상세 화면 인라인 수정 — 운영자 입력 4필드 일괄 갱신.
     */
    public void updateOperatorInfo(String name, String modelName, String serialNumber, String memo) {
        this.name = name;
        this.modelName = modelName;
        this.serialNumber = serialNumber;
        this.memo = memo;
    }

    /**
     * 서버 회수 — 회수 시각 기록. 이미 회수된 경우 최초 시각을 보존한다(멱등).
     */
    public void decommission(LocalDateTime at) {
        if (this.decommissionedAt == null) {
            this.decommissionedAt = at;
        }
    }
}
