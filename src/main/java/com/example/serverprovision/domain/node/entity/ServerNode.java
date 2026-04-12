package com.example.serverprovision.domain.node.entity;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.node.dto.ServerNodeCreateDTO;
import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.model.enums.ProvisioningStatus;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 물리 서버 노드를 표현하는 중심 엔티티이다.
 *
 * <p>역할: 프로비저닝 대상 물리 서버 한 대의 식별 정보({@code macAddress}), IPMI 원격 제어 정보,
 * OS 프로비저닝용 네트워크 정보, 할당된 {@code ServerSetting}(세팅 주문서), 그리고 현재 작업
 * 상태({@code status}, {@code targetJob})를 관리한다.</p>
 *
 * <p>유스케이스: PXE 부팅 시 {@code PXEBootRestController}가 MAC 주소로 이 엔티티를 조회하며,
 * 미등록 MAC이면 {@code ServerNodeService#getOrRegisterNode}를 통해 새 인스턴스가 생성되어
 * {@code ProvisioningStatus.NEW} 상태로 저장된다. {@code ProvisioningScriptService#generateIPXEScript}는
 * {@code serverSetting}의 {@code SettingProcess}를 기반으로 {@code NodeStepExecution} 이력에서
 * 다음 실행할 단계를 조회하여 iPXE 스크립트를 생성한다. {@code startProvisioning}은 작업 시작 시
 * {@code status}를 {@code IN_PROGRESS}로 전이시킨다.</p>
 *
 * <p>확장 가이드: 새로운 하드웨어 속성(예: 네트워크 인터페이스 상세, 메모리 용량)을 추가할 경우
 * 이 엔티티에 필드를 추가하고 {@code ServerNodeCreateDTO}와 관련 폼 템플릿도 함께 갱신한다.
 * 상태 전이 로직을 추가할 때는 {@code ProvisioningStatus} 열거형과 {@code NodeStepExecution}의
 * {@code StepExecutionStatus}가 의미상 충돌하지 않도록 {@code ProvisioningScriptService}와
 * 함께 검토한다.</p>
 */
@Entity
@Table(name = "server_node")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ServerNode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PXE 부팅 요청의 기준 식별자인 MAC 주소이다.
     * {@code ServerNodeRepository#findAvailableNodeByMacAddress}가 이 필드로 노드를 조회한다.
     */
    @Column(name = "mac_address")
    private String macAddress;

    // --- 원격 제어(IPMI) 정보 ---

    /** IPMI 원격 제어 접속 IP 주소이다. */
    @Column(name = "ipmi_ip")
    private String ipmiIp;

    /** IPMI 원격 제어 접속 사용자 이름이다. */
    @Column(name = "ipmi_user")
    private String ipmiUser;

    /** IPMI 원격 제어 접속 비밀번호이다. */
    @Column(name = "ipmi_password")
    private String ipmiPassword;

    // --- OS 프로비저닝(Kickstart)용 네트워크 정보 ---

    /** OS 설치 후 서버에 부여할 호스트명이다. Kickstart 스크립트 생성 시 참조된다. */
    @Column(name = "hostname")
    private String hostname;

    /** OS 설치 후 서버에 부여할 고정 IP 주소이다. Kickstart 스크립트 생성 시 참조된다. */
    @Column(name = "assigned_ip")
    private String assignedIp;

    /**
     * 이 서버에 장착된 메인보드 모델이다. PXE 부팅 요청 시 vendor/boardModel 파라미터로
     * {@code BoardModelRepository}에서 조회되어 연결된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_model_id", nullable = false)
    private BoardModel boardModel;

    // --- 상태 통제 정보 ---

    /** 현재 이 서버에 할당된 작업 유형이다. 최초 등록 시 {@code JobType.IDLE}로 설정된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_job")
    private JobType targetJob;

    /**
     * 현재 프로비저닝 진행 상태이다. 최초 등록 시 {@code ProvisioningStatus.NEW}로 설정되며,
     * {@code startProvisioning} 호출 시 {@code IN_PROGRESS}로 전이된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProvisioningStatus status;

    /**
     * 이 서버에 할당된 세팅 주문서이다. {@code null}이면 {@code ProvisioningScriptService}가
     * 로컬 디스크 부팅 스크립트를 반환한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_setting_id")
    private ServerSetting serverSetting;

    /**
     * 프로비저닝 시작 시 상태를 {@code IN_PROGRESS}로 전이시킨다.
     */
    public void startProvisioning() {
        this.status = ProvisioningStatus.IN_PROGRESS;
    }

    /**
     * 이 서버 노드에 세팅 주문서를 할당하고 작업 유형을 갱신한다.
     *
     * @param setting   할당할 세팅 주문서
     * @param targetJob 갱신할 작업 유형
     */
    public void assignSetting(ServerSetting setting, JobType targetJob) {
        this.serverSetting = setting;
        this.targetJob = targetJob;
    }

    /**
     * 관리자 폼 제출({@code ServerNodeCreateDTO})로 서버 노드를 생성한다.
     * {@code boardModelId}가 {@code null}이거나 DTO의 ID와 실제 엔티티 ID가 불일치하면
     * {@code IllegalArgumentException}을 던진다.
     *
     * @param dto        노드 생성 요청 DTO
     * @param boardModel 조회된 {@code BoardModel} 엔티티
     * @return 초기 상태({@code IDLE}/{@code NEW})의 {@code ServerNode}
     * @throws IllegalArgumentException {@code boardModelId} 불일치 또는 {@code null}
     */
    public static ServerNode create(ServerNodeCreateDTO dto, BoardModel boardModel) {

        if (dto.boardModelId() == null || boardModel == null) {
            throw new IllegalArgumentException("BoardModel 정보가 필요합니다.");
        }
        if (!dto.boardModelId().equals(boardModel.getId())) {
            throw new IllegalArgumentException("DTO 의 BoardModel ID와 실제 BoardModel 의 ID가 일치하지 않습니다.");
        }

        return ServerNode.builder()
                .macAddress(dto.macAddress())
                .ipmiIp(dto.ipmiIp())
                .ipmiUser(dto.ipmiUser())
                .ipmiPassword(dto.ipmiPassword())
                .hostname(dto.hostname())
                .assignedIp(dto.assignedIp())
                .boardModel(boardModel)
                .targetJob(JobType.IDLE)
                .status(ProvisioningStatus.NEW)
                .build();
    }

    /**
     * PXE 부팅 자동 감지 경로로 최소 정보(MAC 주소 + 보드 모델)만으로 서버 노드를 생성한다.
     * IPMI 정보, 호스트명, 고정 IP는 추후 관리자가 직접 설정한다.
     *
     * @param macAddress  감지된 MAC 주소
     * @param boardModel  PXE 부팅 요청에서 확인된 {@code BoardModel} 엔티티
     * @return 초기 상태({@code IDLE}/{@code NEW})의 {@code ServerNode}
     */
    public static ServerNode create(String macAddress, BoardModel boardModel) {
        return ServerNode.builder()
                .macAddress(macAddress)
                .boardModel(boardModel)
                .targetJob(JobType.IDLE)
                .status(ProvisioningStatus.NEW)
                .build();
    }
}
