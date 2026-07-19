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
     * 게스트 신원 토큰(E1-0b, DEC-5) — 부팅 스크립트의 커널 인자로 전달되고 에이전트 API 가 대조한다.
     * U1 기존 등록분은 null 일 수 있어 /boot 재진입 시 lazy 발급({@link #issueTokenIfAbsent()}).
     */
    @Convert(converter = com.example.serverprovision.execution.converter.GuestTokenConverter.class)
    @Column(name = "guest_token", length = 32, unique = true)
    private com.example.serverprovision.execution.vo.GuestToken guestToken;

    /**
     * 게스트 마지막 접촉 시각(E1-2, DEC-32) — /boot 폴링 · 에이전트 보고가 갱신하는 <b>관찰 로그</b>다.
     * dispatch 판정 입력이 아니며(DEC-2 읽기 전용 판정 유지) UI 의 "접촉 중 / 무접촉 N분" 표시와
     * 무보고 침묵(UC-4) 감지에만 쓰인다. 회차 모델(DEC-29) 도입 후에도 서버당 1개면 충분해
     * progress 가 아닌 여기 둔다(plan Q5).
     */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

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

    /** 게스트 접촉 표식(E1-2) — 항상 최신으로 덮는다(관찰 로그라 순서 보정 불요). */
    public void touchSeen(LocalDateTime at) {
        this.lastSeenAt = at;
    }

    /** 토큰 lazy 발급(멱등) — U1 기존 등록분(null) 보정 경로. 발급됐으면 보존(회전 없음, DEC-5). */
    public com.example.serverprovision.execution.vo.GuestToken issueTokenIfAbsent() {
        if (this.guestToken == null) {
            this.guestToken = com.example.serverprovision.execution.vo.GuestToken.issue();
        }
        return this.guestToken;
    }
}
