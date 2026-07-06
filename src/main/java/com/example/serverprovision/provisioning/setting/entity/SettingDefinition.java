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
 * <p>상태 개념이 없다(U2-3 Q1 확정): 정의서는 여러 서버가 참조할 재사용 템플릿이고 실행 상태는
 * 서버 개별 실행의 속성으로 execution({@code ProvisioningProgress})이 소유한다. 수정은 자유
 * (BIOS 템플릿 철학 통일)이며, 삭제·참조 차단은 서버 할당이 생기는 U3 에서 도입한다.</p>
 */
@Entity
@Table(name = "setting_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingDefinition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 전역 유일 명칭(D3 — select 식별, 중복 409). */
    @Column(nullable = false, length = 128, unique = true)
    private String name;

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
}
