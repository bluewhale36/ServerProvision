package com.example.serverprovision.provisioning.assignment.entity;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhasesConverter;
import com.example.serverprovision.provisioning.assignment.vo.SourceDefinitionRef;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 세팅 정의서를 게스트 서버에 할당한 <b>스냅샷</b>(할당 시점 즉시 · 행 단위 복사, DEC-16·17).
 *
 * <p>정의서 참조는 {@link SourceDefinitionRef} <b>소프트참조</b>(FK 없음)라 원본 정의서 purge 와 무관하게
 * 생존한다. 상태 컬럼을 두지 않고 {@code consumedAt}(개시 시 소비) · {@code supersededAt}(재할당 시 논리 종료)
 * 두 시각에서 {@link #state()} 로 파생한다({@code GuestServer} §D4 하우스스타일). 활성 술어는
 * {@code supersededAt IS NULL} 단일(결정 D-A) — 게스트당 활성 스냅샷 1개 불변식을 서비스 가드 + {@code @Version}
 * 이 지킨다.</p>
 */
@Entity
@Table(name = "setting_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingAssignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 게스트(execution) — 할당은 provisioning→execution 단방향 참조. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_server_id", nullable = false, updatable = false)
    private GuestServer guestServer;

    /** 원본 정의서 소프트참조(id 스칼라 + 표시명 스냅샷). */
    @Embedded
    private SourceDefinitionRef sourceDefinitionRef;

    /** derive-then-freeze 된 소유 phase — phase 진행의 유일 권위(결정 D-G). 안정 코드 CSV 컬럼. */
    @Convert(converter = OwnedPhasesConverter.class)
    @Column(name = "owned_phases", nullable = false, length = 128)
    private OwnedPhases ownedPhases;

    /** 소비 시각(개시 시 markConsumed). null = 미소비 — 파생 아닌 저장값. */
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    /** 논리 종료 시각(재할당 supersede). null = 활성 — 활성 술어의 단일 근거. */
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Version
    private Long version;

    /** 행 단위 복사된 단계 스냅샷(aggregate 종속 — cascade + orphanRemoval). */
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignedProcess> processes = new ArrayList<>();

    private SettingAssignment(GuestServer guestServer, SourceDefinitionRef sourceDefinitionRef, OwnedPhases ownedPhases) {
        this.guestServer = guestServer;
        this.sourceDefinitionRef = sourceDefinitionRef;
        this.ownedPhases = ownedPhases;
    }

    /** 정의서 스냅샷에서 생성(단계 행은 {@link #addProcess} 로 장착). */
    public static SettingAssignment create(GuestServer guestServer, SourceDefinitionRef sourceDefinitionRef, OwnedPhases ownedPhases) {
        return new SettingAssignment(guestServer, sourceDefinitionRef, ownedPhases);
    }

    /** 단계 스냅샷 장착 — 양방향 연관을 함께 세팅. */
    public void addProcess(AssignedProcess process) {
        this.processes.add(process);
        process.assignTo(this);
    }

    /** 파생 상태(저장 컬럼 없음). */
    public AssignmentState state() {
        if (supersededAt != null) {
            return AssignmentState.SUPERSEDED;
        }
        return consumedAt != null ? AssignmentState.ACTIVE_CONSUMED : AssignmentState.ACTIVE_UNCONSUMED;
    }

    /**
     * 소비 표식(개시 시). 멱등 — 이미 소비됐으면 최초 시각을 보존한다. 종료(superseded)된 스냅샷의 소비는
     * 프로그램 버그(UI 1차 차단 + 활성 유일성 가드를 뚫은 경로)이므로 안전망 예외로 남긴다.
     */
    public void markConsumed(LocalDateTime now) {
        if (supersededAt != null) {
            throw new IllegalStateException("종료된 할당 스냅샷은 소비할 수 없습니다. id=" + id);
        }
        if (consumedAt == null) {
            this.consumedAt = now;
        }
    }

    /** 논리 종료(재할당 — U3-2 에서 호출). 멱등. */
    public void supersede(LocalDateTime now) {
        if (supersededAt == null) {
            this.supersededAt = now;
        }
    }

    public boolean isActive() {
        return supersededAt == null;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
