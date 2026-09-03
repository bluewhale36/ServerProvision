package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 세부 단계 체크포인트 — 한 서버의 단계 실행 이력. guest_server 와 1:N (append-only).
 *
 * <p>U1 §D7 : 옛 {@code @OneToOne} (서버당 1행 한정 결함)을 {@code @ManyToOne} 으로 바로잡는다.
 * {@code phase} 는 {@code stepCode.getPhaseType()} 로 항상 파생되므로 별도 컬럼을 두지 않고 {@link #phase()} 로 도출한다.
 * 행 적재는 프로비저닝 엔진(Stage 4)의 책임 — U1 은 모양만 갖춘다.</p>
 */
@Entity
@Table(name = "provisioning_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProvisioningHistory extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", length = 25)
    private ProvisioningPhaseStep stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    private ProvisioningStatus status;

    @Column(name = "status_meta", columnDefinition = "json")
    private String statusMeta;

    /** {@link ProvisioningStatus#PENDING} 상태의 경우 {@code null}. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 전체 작업이 종료되지 않았을 경우 {@code null}. */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 운영자 수동 실패 전환 행의 statusMeta(ES-2 D-5) — 옛 {@code failed_step_code} null 표식을 대체하는
     * 원장 기록. 작성(GuestServerCommandService)과 판독({@link #isOperatorOrigin})이 이 상수 하나를 공유한다.
     */
    public static final String OPERATOR_ORIGIN_META = "{\"origin\":\"operator\"}";

    /** 소속 Phase — stepCode 에서 도출(별도 저장 없음, §D7). */
    public ProvisioningPhase phase() {
        return stepCode != null ? stepCode.getPhaseType() : null;
    }

    /**
     * 자원 결손 대기의 시한 만료로 실패 전환된 기록의 statusMeta(E2-1-b) — 사유(어느 축이 왜 막혔는지)와
     * 시한은 나중에 파생할 수 없으므로 사건 시점에 적는다. 값은 ASCII 코드 요약이라 이스케이프가 필요 없다.
     */
    public static String holdTtlMeta(String wireSummary, java.time.Duration ttl) {
        return "{\"origin\":\"hold-ttl\",\"ttl\":\"" + ttl + "\",\"shortage\":\"" + wireSummary + "\"}";
    }

    /** 이 행이 자원 결손 시한 만료의 기록인가(E2-1-b) — 재시도 차단 판정이 이 사실을 읽는다. */
    public boolean isHoldTtlOrigin() {
        return statusMeta != null && statusMeta.contains("\"origin\":\"hold-ttl\"");
    }

    /**
     * 굽기를 시작하며 여는 행의 statusMeta(E2-2 D-4) — <b>무엇을 어느 Task 로 굽는지</b>를 사건 시점에 적는다.
     *
     * <p>적어 두는 이유가 셋이다. ① 굽는 동안 운영자가 자원을 바꿔도 확인 기준이 흔들리지 않는다.
     * ② 워커가 다음 주기에 이 게스트를 다시 집을 때 <b>어디까지 갔는지를 원장에서 복원</b>한다 —
     * 워커가 메모리에 상태를 들고 있으면 재기동에 취약하다. ③ 나중에 "이 서버에 무엇을 구웠나" 를
     * 되짚을 유일한 근거다. 값은 버전 문자열과 서버 생성 경로뿐이라 이스케이프가 필요 없다.</p>
     */
    public static String flashTargetMeta(String targetVersion, Long firmwareId, String taskPath) {
        return flashTargetMeta(null, targetVersion, firmwareId, taskPath);
    }

    /** 자원 이름을 함께 싣는 변형(E2-4 R7) — 표시용이며, 대조 재료(target)는 버전 그대로 둔다. */
    public static String flashTargetMeta(String resourceName, String targetVersion, Long firmwareId, String taskPath) {
        return "{\"origin\":\"flash\""
                + (resourceName == null ? "" : ",\"name\":\"" + resourceName + "\"")
                + ",\"target\":\"" + targetVersion + "\",\"firmwareId\":" + firmwareId
                + ",\"task\":\"" + (taskPath == null ? "" : taskPath) + "\"}";
    }

    /**
     * 축 종결 · 실패 행의 statusMeta(E2-2). 사유 어휘를 나누는 것은 <b>운영자가 할 일이 다르기</b>
     * 때문이다 — 파일을 볼지, 장비 상태를 볼지, 네트워크를 볼지, 주소가 어긋난 원인을 급히 찾을지가
     * 사유마다 갈린다. 뭉치면 원장을 읽고도 어디부터 봐야 할지 알 수 없다.
     */
    public static String flashOutcomeMeta(String reason, String detail) {
        return "{\"origin\":\"" + reason + "\",\"detail\":\"" + (detail == null ? "" : detail) + "\"}";
    }

    /**
     * 굽기 행을 결과로 닫는다(E2-2) — <b>무엇을 구웠는지는 지우지 않는다.</b>
     *
     * <p>{@link #close}는 statusMeta 를 통째로 갈아 끼운다. 그 계약을 그대로 쓰면 열림 시점에 적어 둔
     * 목표 · 자원 id · Task 경로가 닫는 순간 사라지고, 그러면 재부팅 뒤 <b>무엇과 대조해야 하는지를
     * 잃는다</b> — 반영 확인이 대조 대상 없이 통과해 버린다(CP5 F-1 실측). 굽기는 "무엇을 했는가" 와
     * "어떻게 끝났는가" 가 <b>둘 다 사건 사실</b>이라 한쪽이 다른 쪽을 덮으면 안 된다.</p>
     */
    public boolean closeFlash(ProvisioningStatus result, String reason, String detail, LocalDateTime at) {
        String target = flashTargetVersion();
        String task = flashTaskPath();
        String name = flashResourceName();
        String preserved = target == null
                ? flashOutcomeMeta(reason, detail)
                : "{\"origin\":\"" + reason + "\",\"detail\":\"" + (detail == null ? "" : detail)
                        + (name == null ? "" : "\",\"name\":\"" + name)
                        + "\",\"target\":\"" + target + "\",\"task\":\"" + (task == null ? "" : task) + "\"}";
        return close(result, preserved, at);
    }

    /**
     * 열린(RUNNING) 행의 statusMeta 를 갱신한다(E3-1 D-5) — 설정 적용은 열림 뒤에 아는 사실(재부팅 시각 ·
     * pending 관찰)을 같은 행에 덧써야 다음 주기가 원장에서 상태를 복원한다. 닫힌 행은 사건 기록이라 손대지 않는다.
     * 조립 · 판독은 호출자(SettingLedger)가 JSON 으로 한다 — 목표가 맵이라 문자열 조립이 맞지 않는다.
     */
    public boolean updateRunningMeta(String statusMeta) {
        if (this.status != ProvisioningStatus.RUNNING) {
            return false;
        }
        this.statusMeta = statusMeta;
        return true;
    }

    /** 이 행이 굽기 시작 기록이면 그 Task 경로 — 없으면 null(워커가 상태를 복원할 때 읽는다). */
    public String flashTaskPath() {
        return extractMeta("task");
    }

    /** 이 행이 굽기 시작 기록이면 그 목표 버전 — 반영 확인이 이 값과 대조한다(D-4). */
    public String flashTargetVersion() {
        return extractMeta("target");
    }

    /** 이 행의 사유 코드 — 화면이 무엇 때문에 멈췄는지 가릴 때 읽는다(E2-2). */
    public String flashFailureReason() {
        return extractMeta("origin");
    }

    /** 이 행이 굽기 기록이면 그 자원 이름(E2-4 R7) — 없으면 null(구 행 호환 · 화면은 버전만 표기). */
    public String flashResourceName() {
        return extractMeta("name");
    }

    /**
     * 화면용 사유 한 줄(E2-4 R5) — detail(사람 문구) 우선, 없으면 origin 코드. flash · setting 계열이
     * 같은 두 키를 쓰므로 판독 하나로 통일된다. 시작 마커(flash · setting)와 운영자 마커는 사유가 아니라 제외.
     */
    public String displayNote() {
        String detail = extractMeta("detail");
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        String origin = extractMeta("origin");
        if (origin != null && !origin.isBlank()
                && !"flash".equals(origin) && !"setting".equals(origin) && !"operator".equals(origin)) {
            return origin;
        }
        return absorbedNote();
    }

    /**
     * 관용 흡수 목록(E3.5-5-a CP5 F-1) — 진단 소비가 적재를 생략한 축(placeholder 필터 · 시리얼 중복 ·
     * raid(TOOL_MISSING) 등)은 INFORMATION_PERSISTING 행의 {@code filtered} 배열에 남는다. 사유 detail · origin 이
     * 없을 때의 폴백으로 한 줄에 잇는다 — 운영자가 DB 를 열지 않아도 "왜 비었는지" 를 이력 표에서 본다.
     */
    private String absorbedNote() {
        tools.jackson.databind.JsonNode root = metaRoot();
        if (root == null) {
            return null;
        }
        tools.jackson.databind.JsonNode filtered = root.path("filtered");
        if (!filtered.isArray() || filtered.isEmpty()) {
            return null;
        }
        java.util.List<String> items = new java.util.ArrayList<>();
        filtered.forEach(n -> {
            if (n.isValueNode()) {
                items.add(n.asString());
            }
        });
        return items.isEmpty() ? null : String.join(" · ", items);
    }

    /** 사람이 읽을 상세 — 사유만으로는 부족한 맥락(목표 버전 · 확인값 등). */
    public String flashDetail() {
        return extractMeta("detail");
    }

    private static final tools.jackson.databind.ObjectMapper META_READER = new tools.jackson.databind.ObjectMapper();

    /**
     * statusMeta 의 <b>최상위</b> 문자열 필드 하나. 옛 얕은 문자열 탐색은 게스트 원문 보고(INFORMATION_COLLECTING)에
     * 중첩된 {@code raid.detail} 을 그 행의 사유로 주워 왔다(E3.5-5-a CP5 F-2) — 원장 사유는 서버가 조립한 최상위 키뿐이므로
     * JSON 으로 읽어 최상위만 본다. 평문 · 손상 메타는 판독할 것이 없어 null.
     */
    private String extractMeta(String field) {
        tools.jackson.databind.JsonNode root = metaRoot();
        if (root == null) {
            return null;
        }
        tools.jackson.databind.JsonNode v = root.path(field);
        return v.isValueNode() && !v.isNull() ? v.asString() : null;
    }

    private tools.jackson.databind.JsonNode metaRoot() {
        if (statusMeta == null || statusMeta.isBlank()) {
            return null;
        }
        try {
            tools.jackson.databind.JsonNode node = META_READER.readTree(statusMeta);
            return node != null && node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 이 행이 운영자 액션의 기록인가(ES-2 D-5) — 화면의 '운영자 전환' 구분이 이 판정을 파생한다. */
    public boolean isOperatorOrigin() {
        return statusMeta != null && statusMeta.contains("\"origin\":\"operator\"");
    }

    /**
     * 단발 기록 팩토리(E1-0a, DEC-3) — "판정 즉시 적재" 되는 서버 측 step 은 시작 = 종료 시각이다.
     * ID 생성(UUID v7 — PK 클러스터링)까지 캡슐화해 적재자마다의 ID 조립 중복을 막는다.
     */
    public static ProvisioningHistory instant(
            GuestServer guestServer, ProvisioningPhaseStep stepCode,
            ProvisioningStatus status, String statusMeta, LocalDateTime at) {
        return ProvisioningHistory.builder()
                .id(org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null))
                .guestServer(guestServer)
                .stepCode(stepCode)
                .status(status)
                .statusMeta(statusMeta)
                .startedAt(at)
                .finishedAt(at)
                .build();
    }

    /** 게스트 실행 step 의 열림 팩토리(E1-0b, DEC-3) — 시작 보고 시점에 RUNNING 으로 생성된다. */
    public static ProvisioningHistory openRunning(GuestServer guestServer, ProvisioningPhaseStep stepCode, LocalDateTime at) {
        return openRunning(guestServer, stepCode, at, null);
    }

    /**
     * 여는 시점에 이미 알고 있는 사실을 함께 싣는 변형(E2-2) — 무엇을 어느 Task 로 굽는지가 그렇다.
     * 나중에 채우려면 열림과 기록 사이에 그 사실이 없는 창이 생기고, 그 사이에 워커가 재기동되면
     * 진행 중인 굽기를 원장에서 복원하지 못한다.
     */
    public static ProvisioningHistory openRunning(GuestServer guestServer, ProvisioningPhaseStep stepCode,
                                                  LocalDateTime at, String statusMeta) {
        return ProvisioningHistory.builder()
                .statusMeta(statusMeta)
                .id(org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null))
                .guestServer(guestServer)
                .stepCode(stepCode)
                .status(ProvisioningStatus.RUNNING)
                .startedAt(at)
                .build();
    }

    /**
     * 종료 보고(닫힘) — append-only 원장에서 허용되는 유일한 행 갱신(RUNNING → 종결 1회).
     * 이미 종결된 행이면 아무것도 바꾸지 않고 {@code false} — 중복 종료 보고 no-op 멱등의 실체.
     */
    public boolean close(ProvisioningStatus result, String statusMeta, LocalDateTime at) {
        if (this.finishedAt != null) {
            return false;
        }
        this.status = result;
        this.statusMeta = statusMeta;
        this.finishedAt = at;
        return true;
    }
}
