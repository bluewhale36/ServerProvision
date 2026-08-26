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

    /**
     * SMBIOS systemUUID — 등록 정체성. 단순 UNIQUE 가 아니라 <b>활성 한정 UNIQUE</b> 다(U6):
     * DB 의 PERSISTENT generated column({@code active_system_uuid}, JPA 미매핑)이 활성 행만
     * 유일을 강제하고, 회수 행은 같은 UUID 로 얼마든 쌓인다 — "회수 후 재시도" 등록의 전제.
     */
    @Column(name = "system_uuid", nullable = false)
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
     * 전원 조작 차단 사유 SSOT (U6 CP6 검수 — R13 후속의 "굽는 중" 차단과 통합) — 할 수 있으면 {@code null}.
     * 상세 화면의 전원 버튼 disabled + tooltip 과 {@code GuestServerPowerRestController} 의 reset 가드가
     * 이 한 메서드를 함께 부른다. 회수를 먼저 본다({@code GuestServerStatus.derive} 가 회수를 최우선 상태로
     * 두는 정렬과 같다). 상태 조회(읽기)는 막지 않는다.
     */
    public String powerControlBlockReason(ProvisioningProgress progress) {
        if (decommissionedAt != null) {
            return "회수된 서버는 전원을 조작할 수 없습니다.";
        }
        if (progress != null && progress.isDisruptionBlocked()) {
            return "펌웨어를 굽는 중에는 사용할 수 없습니다.";
        }
        return null;
    }

    /**
     * 영구 삭제 차단 사유 SSOT (U6 D-5) — 지울 수 있으면 {@code null}. 상세 화면의 삭제 섹션
     * 노출 판정과 {@code GuestServerCommandService.purge} 가드가 이 한 메서드를 함께 부른다.
     */
    public String purgeBlockReason() {
        if (decommissionedAt == null) {
            return "회수된 서버만 영구 삭제할 수 있습니다.";
        }
        return null;
    }

    /**
     * 삭제 확인 입력의 기대값 — systemUUID 의 마지막 {@code -} 다음 세그먼트(U6 사용자 확정).
     * 화면 안내와 서버 가드가 같은 값을 쓴다. 형식이 다른 장비 대응은 MVP 이후.
     */
    public String systemUUIDSuffix() {
        String raw = systemUUID.toString();
        return raw.substring(raw.lastIndexOf('-') + 1);
    }

    /**
     * 서버 회수 — 회수 시각 기록. 이미 회수된 경우 최초 시각을 보존한다(멱등).
     */
    public void decommission(LocalDateTime at) {
        if (this.decommissionedAt == null) {
            this.decommissionedAt = at;
        }
    }

    /**
     * 세팅 정의서 할당 차단 사유 SSOT (U3-5-a) — 붙일 수 있으면 {@code null}, 아니면 <b>화면 안내이자
     * 서버 거절 사유</b>가 되는 문자열이다.
     *
     * <p>{@code SettingAssignmentSnapshot.reassignBlockReason()} · {@code SettingDefinition.assignBlockReason()} ·
     * {@code GuestServerGroup.addBlockReason()} 과 같은 형태로, 서버 상세의 폼 노출 판정과
     * {@code AssignmentCommandService} 의 가드가 이 한 메서드를 함께 부른다. 두 곳에 조건을 복붙하면
     * 드리프트가 생긴다.</p>
     *
     * <p>회수된 서버는 개시({@code ProvisioningProgress.isStartableWith})와 수동 실패 전환에서 이미
     * 막히는데 <b>할당만 열려 있었다</b> — 이 메서드가 그 공백을 메운다. 회수는
     * {@code GuestServerStatus.derive} 가 신호와 무관한 최우선으로 두는 상태이므로 다른 어떤 판정보다
     * 먼저 본다.</p>
     */
    public String assignBlockReason() {
        return decommissionedAt != null
                ? "회수된 서버에는 세팅 정의서를 할당할 수 없습니다."
                : null;
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
