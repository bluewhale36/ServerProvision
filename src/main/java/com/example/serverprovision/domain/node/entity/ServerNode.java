package com.example.serverprovision.domain.node.entity;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.domain.node.model.enums.BoardModel;
import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.model.enums.ProvisioningStatus;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "server_node")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ServerNode extends BaseTimeEntity {
    @Id
    @Column(name = "mac_address")
    private String macAddress; // 기본키: 통신의 기준이 되는 MAC 주소

    // --- 원격 제어(IPMI) 정보 ---
    @Column(name = "ipmi_ip")
    private String ipmiIp;

    @Column(name = "ipmi_user")
    private String ipmiUser;

    @Column(name = "ipmi_password")
    private String ipmiPassword;

    // --- OS 프로비저닝(Kickstart)용 네트워크 정보 ---
    @Column(name = "hostname")
    private String hostname;

    @Column(name = "assigned_ip")
    private String assignedIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor")
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_model")
    private BoardModel boardModel;

    // --- 상태 통제 정보 ---
    @Enumerated(EnumType.STRING)
    @Column(name = "target_job")
    private JobType targetJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProvisioningStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_setting_id")
    private ServerSetting serverSetting;

    @Column(name = "current_step_index")
    private Integer currentStepIndex = 0;

    // 단계 완료 시 인덱스를 증가시키는 메서드
    public void advanceStepIndex() {
        if (this.currentStepIndex != null) {
            this.currentStepIndex++;
        }
    }

    // 프로비저닝 시작 시 초기화
    public void startProvisioning() {
        this.status = ProvisioningStatus.IN_PROGRESS;
        this.currentStepIndex = 0;
    }
}
