package com.example.serverprovision.provisioning.biossetting.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValuesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * BIOS 세팅 정의서(템플릿) — 특정 보드({@link BoardModel} FK)의 BIOS 설정 변경분 스냅샷.
 *
 * <p>보드 귀속은 varchar boardKey 에서 {@code board_model} FK 로 전환됐다(사용자 지시 2026-07-05,
 * BoardBIOS 선례와 동일 관계 성격 — ON DELETE CASCADE). BIOS 카탈로그(registry/SetupData 파일) 해석 키는
 * {@code boardModel.modelName} 이며(DB 실데이터가 카탈로그 키와 동일 표기), 이 통일로 guest 정의서의
 * 보드 selector(boardModelId)와 템플릿 보드가 같은 식별자 축이 되어 U2-2-3 의 정합 검증이 가능해진다.</p>
 *
 * <p>LifecycleEntity/Markable 을 상속하지 않는다(U2-2 설계 report 확정): 디스크 파일이 없는 순수
 * 데이터 자원이라 marker/휴지통/reconciliation 이 지킬 대상이 없고, 오삭제는 사용중 참조 차단
 * 가드(U2-2-3)가 막는다. guest 세팅 정의서의 BASIC_SETTING 단계가 이 엔티티를 id 로 참조한다(REFERENCE).</p>
 */
@Entity
@Table(name = "bios_setting_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BiosSettingTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 전역 유일 명칭 — guest 정의서의 템플릿 select 에서 이름만으로 식별한다(중복 409). */
    @Column(nullable = false, length = 128, unique = true)
    private String name;

    @Column(length = 1024)
    private String description;

    /** 검증에 쓰인 보드 — 생성 후 불변(수정 계약에 미포함). 템플릿의 유효 도메인을 규정한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id", nullable = false, updatable = false)
    private BoardModel boardModel;

    /** 변경분(diff) flat 값 집합 — coerce 후 타입 보존 JSON. */
    @Convert(converter = BiosSettingValuesConverter.class)
    @Column(name = "values_json", nullable = false, columnDefinition = "json")
    private BiosSettingValues values;

    @Builder
    private BiosSettingTemplate(String name, String description, BoardModel boardModel, BiosSettingValues values) {
        this.name = name;
        this.description = description;
        this.boardModel = boardModel;
        this.values = values;
    }

    /** 수정 — boardKey 는 불변(유효 도메인), values 는 전체 교체 의미론. */
    public void update(String name, String description, BiosSettingValues values) {
        this.name = name;
        this.description = description;
        this.values = values;
    }
}
