package com.example.serverprovision.application.setting.domain.entity;

import com.example.serverprovision.application.setting.converter.SettingProcessConverter;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 세팅 주문서를 표현하는 JPA 엔티티이다.
 *
 * <p>역할: 물리 서버에 적용할 프로비저닝 작업 명세를 DB({@code server_setting} 테이블)에
 * 영속화한다. 주문서 명칭({@code name}), 프로세스 단계 목록({@code settingProcess}),
 * 현재 진행 상태({@code status})를 보유한다. 생성·수정 일시는
 * {@link com.example.serverprovision.global.entity.BaseTimeEntity}가 JPA Auditing으로 관리한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * {@code ServerSetting.builder()}로 생성되어 {@link com.example.serverprovision.application.setting.repository.SettingRepository}에
 * 저장된다. 초기 {@code status}는 {@link SettingStatus#PENDING}이다.
 * {@code settingProcess} 컬럼은 {@link SettingProcessConverter}를 통해 JSON 문자열로 직렬화된다.
 * {@link com.example.serverprovision.domain.node.model.ServerNode}이 이 엔티티를 FK로 참조하여
 * 할당된 세팅 주문서를 식별하며, PXE 부팅 흐름에서 {@code currentStepIndex}와 함께
 * 현재 실행해야 할 단계를 결정한다.</p>
 *
 * <p>확장 가이드: 세팅 주문서에 추가 메타데이터(예: 생성자 정보, 적용 예정 일시)가 필요하면
 * 이 엔티티에 필드를 추가하고 {@link com.example.serverprovision.application.setting.dto.SettingCreateRequest},
 * {@link com.example.serverprovision.application.setting.dto.SettingCreateResponse},
 * {@link com.example.serverprovision.application.setting.service.SettingService#save}도 함께 갱신한다.
 * {@code status} 전이 로직을 구현할 때는 PXE 부팅 흐름의
 * {@link com.example.serverprovision.domain.provisioning} 패키지와 연동하여 설계한다.</p>
 */
@Entity
@Table(name = "server_setting")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ServerSetting extends BaseTimeEntity {

    /** DB 자동 생성 기본키이다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 세팅 주문서의 식별 명칭이다. {@code nullable = false}로 반드시 입력해야 한다. */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 이 주문서에 포함된 프로비저닝 단계 목록이다.
     * {@link SettingProcessConverter}를 통해 {@code process} 컬럼에 JSON 문자열로 저장된다.
     * JSON 내부의 각 단계 타입은 {@code "type"} 판별자로 식별된다.
     */
    @Column(name = "process")
    @Convert(converter = SettingProcessConverter.class)
    private SettingProcess settingProcess;

    /**
     * 이 주문서의 현재 진행 상태이다. DB에 문자열로 저장된다.
     * 생성 시 {@link SettingStatus#PENDING}으로 초기화된다.
     */
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SettingStatus status;
}
