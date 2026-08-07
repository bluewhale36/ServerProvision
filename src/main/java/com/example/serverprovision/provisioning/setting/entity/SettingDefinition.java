package com.example.serverprovision.provisioning.setting.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * guest server 세팅 정의서 — 프로비저닝 단계들의 재사용 가능한 명세(템플릿) aggregate.
 *
 * <p>실행 상태(진행 커서)는 없다(U2-3 Q1 확정): 정의서는 여러 서버가 참조할 재사용 템플릿이고 실행 상태는
 * 서버 개별 실행의 속성으로 execution({@code ProvisioningProgress})이 소유한다. 수정은 자유
 * (BIOS 템플릿 철학 통일).</p>
 *
 * <p>U3-2-b 에서 <b>DB 전용 lifecycle</b> 을 도입했다(DEC-A · DEC-G). 부모 없는 · 파일 없는 순수 DB 엔티티라
 * {@code LifecycleEntity}(마커/휴지통/own·effective 9 컬럼) 를 상속하지 않고 {@code is_deleted} ·
 * {@code is_enabled} · {@code is_deprecated} 플래그 3개 + 도메인 메서드만 얹는다. 부모가 없으므로 own/effective
 * 분리도 parentLifecycle cascade 도 불요하다 — 운영자 의도가 곧 실효 상태다.</p>
 *
 * <p>세 축의 의미는 자원 도메인 선례와 동형이다(DEC-G): <b>deleted</b> 는 목록 제외 + 신규 할당 차단,
 * <b>disabled</b> 는 신규 할당만 차단(이미 만들어진 할당 스냅샷은 무영향 — 소프트참조라 원본 상태와 독립),
 * <b>deprecated</b> 는 할당을 막지 않고 "사용 중단 권고" 경고만 표시한다. 삭제는 차단이 아니라 경고이고
 * (정의서 → 할당은 FK 없는 소프트참조라 삭제해도 스냅샷 생존, DEC-C), 활성 name 충돌 가드(restore)·
 * typed-name(purge) 검증은 repository 조회/입력 비교가 필요해 {@code JpaSettingCommandService} 소관이다.</p>
 */
@Entity
@Table(name = "setting_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingDefinition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 명칭(select 식별). U3-2-b 에서 DB UNIQUE(name) 을 제거하고 <b>활성 전용</b> 유일성으로 이행했다(DEC-B) —
     * soft-delete 행이 이름을 영구 점유하는 것을 막기 위함. 유일성은 서비스 가드
     * ({@code existsByNameAndIsDeletedFalse}) 가 지킨다.
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * soft-delete 플래그(U3-2-b, DEC-A). 활성 목록에서 감추는 논리 삭제. 도메인 메서드
     * {@link #softDelete()} / {@link #restore()} 로만 전이하며, 영구 삭제(purge)는 서비스가 hard delete 한다.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * 활성 여부(U3-2-b DEC-G). 비활성이면 <b>신규 할당만</b> 차단하고 기존 할당 스냅샷은 건드리지 않는다 —
     * 스냅샷은 할당 시점 복사본(소프트참조)이라 원본 상태 변화와 독립이다. 신규 생성은 활성으로 시작한다.
     * 필드명이 {@code isEnabled} 인 것은 파생 쿼리 프로퍼티({@code ...IsEnabled...})와 일치시키기 위함이다.
     */
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    /**
     * 사용 중단 권고(U3-2-b DEC-G). 할당을 <b>차단하지 않고</b> 경고만 표시한다 — deprecated 는 disabled 와
     * 독립된 축이다(자원 도메인 선례 동형). 별도 시각 컬럼을 두지 않고 {@code BaseTimeEntity.updatedAt} 으로
     * 최종 변경 시점을 추적한다(최소 원칙).
     */
    @Column(name = "is_deprecated", nullable = false)
    private boolean isDeprecated;

    /**
     * 단계 행들 — aggregate 종속(단방향, cascade + orphanRemoval). 순서 컬럼 없음(D7) —
     * 재조립·표시는 {@code SettingProcessType} enum 선언 순.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "setting_definition_id", nullable = false)
    private List<SettingProcess> processes = new ArrayList<>();

    @Builder
    private SettingDefinition(String name, List<SettingProcess> processes) {
        this.name = name;
        if (processes != null) {
            this.processes.addAll(processes);
        }
    }

    /**
     * 수정 1/2 — 명칭 갱신 + 기존 단계 행 분리(orphanRemoval 대상화). 전체 교체 의미론(D4)의 전반부.
     * 후반부({@link #attachProcesses}) 사이에 flush 가 필요한 이유는 {@code JpaSettingCommandService} 참고.
     */
    public void changeNameAndClearProcesses(String name) {
        this.name = name;
        this.processes.clear();
    }

    /** 수정 2/2 — 새 단계 행 장착. */
    public void attachProcesses(List<SettingProcess> newProcesses) {
        this.processes.addAll(newProcesses);
    }

    /** soft-delete — 활성 목록에서 감춘다. 멱등(이미 삭제됐으면 no-op). {@code isDeleted()} 는 @Getter 생성. */
    public void softDelete() {
        this.isDeleted = true;
    }

    /** 복원 — 다시 활성. 멱등. 활성 name 충돌 가드는 repository 조회가 필요해 Service 소관이다(DEC-B). */
    public void restore() {
        this.isDeleted = false;
    }

    /** 활성 ↔ 비활성 반전(운영자 의도가 곧 실효 상태 — 부모가 없어 재계산할 effective 가 없다). */
    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    /** 사용 중단 권고 표시. 멱등(이미 권고 중이면 no-op) — 상태 전이가 아니라 표시 플래그다. */
    public void deprecate() {
        this.isDeprecated = true;
    }

    /** 사용 중단 권고 해제. 멱등. */
    public void undeprecate() {
        this.isDeprecated = false;
    }

    /**
     * 신규 할당 차단 사유 SSOT(U3-2-b DEC-G) — {@code null} 이면 할당 가능, 문자열이면 그게 UI 안내이자 서버
     * 가드 예외 메시지다. {@code SettingAssignment.reassignBlockReason()} 선례와 동형으로 <b>서버 가드</b>
     * ({@code AssignmentCommandService} 의 assign · reassign)와 <b>뷰</b>(할당 드롭다운 옵션 필터)가 이 한
     * 메서드를 함께 호출해 drift 를 없앤다.
     *
     * <p>deprecated 는 차단 사유가 아니다 — 사용 중단을 권고할 뿐 할당은 허용하고(경고 표시), 이미 만들어진
     * 할당 스냅샷은 세 축 어느 것으로도 영향받지 않는다(소프트참조).</p>
     */
    public String assignBlockReason() {
        if (isDeleted) {
            return "삭제된 정의서는 할당할 수 없습니다";
        }
        if (!isEnabled) {
            return "비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)";
        }
        return null;
    }
}
