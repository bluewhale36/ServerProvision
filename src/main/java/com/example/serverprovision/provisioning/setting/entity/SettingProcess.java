package com.example.serverprovision.provisioning.setting.entity;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayloadConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정의서의 프로비저닝 단계 1행 — 다형 payload(계약 원문)의 영속 단위.
 *
 * <p>D7 — sort 컬럼 없음: 실행 순서는 타입에 내장(execution 소유)이고 표시·재조립은
 * {@link SettingProcessType} enum 선언 순으로 결정적이다. {@code (definition, process_type)}
 * UNIQUE 가 "타입당 최대 1개" UI 규칙의 DB 안전망(같은 규칙을 요청-로컬 @AssertTrue 가 400 으로 선차단).
 * {@code processType} 컬럼은 payload 에서 파생되는 질의 전용 값 — SSOT 는 payload({@code D1}).</p>
 *
 * <p>생명주기는 {@link SettingDefinition} aggregate 에 종속(cascade + orphanRemoval — 수정은
 * 전체 교체 의미론 D4). U2-2-3 에서 BASIC_SETTING 행의 템플릿 참조 조인 테이블이 이 행을 부모로 갖는다.</p>
 */
@Entity
@Table(name = "setting_process",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_setting_process_type",
                columnNames = {"setting_definition_id", "process_type"}
		)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** payload 파생 판별자(질의·목록 요약용) — 쓰기 시 {@link ProcessPayload#processType()} 에서 단방향 생성. */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false, length = 32)
    private SettingProcessType processType;

    /** 계약 원문(판별자 포함 flat JSON) — 저장의 SSOT. */
    @Convert(converter = ProcessPayloadConverter.class)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private ProcessPayload payload;

    /**
     * BIOS 세팅 템플릿 참조의 파생 행(U2-2-3 D1) — payload 에서 단방향 생성되는 질의·무결성
     * 전용({@code process_type} 컬럼과 동일 지위, 읽기 재조립 미관여). 템플릿 쪽 FK 는 DDL 에서
     * RESTRICT — 사용중 템플릿 삭제의 최후 방어선.
     */
    @ElementCollection
    @CollectionTable(name = "setting_process_bios_template",
            joinColumns = @JoinColumn(name = "setting_process_id"))
    @Column(name = "bios_setting_template_id", nullable = false)
    private java.util.Set<Long> templateRefs = new java.util.LinkedHashSet<>();

    public SettingProcess(ProcessPayload payload) {
        this.payload = payload;
        this.processType = payload.processType();
        this.templateRefs = new java.util.LinkedHashSet<>(
                payload.request().referencedBiosSettingTemplateIds());
    }
}
