package com.example.serverprovision.domain.node.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.model.*;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.repository.SettingRepository;
import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import com.example.serverprovision.domain.node.entity.NodeStepExecution;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.model.enums.ProvisioningStatus;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import com.example.serverprovision.domain.node.repository.NodeStepExecutionRepository;
import com.example.serverprovision.domain.node.repository.ServerNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * {@link ServerNodeService#assignSetting} 단위 테스트.
 *
 * <p>외부 의존성(Repository)은 전부 Mock 처리하며,
 * private 메서드 {@code determineTargetJob}은 {@code assignSetting}의 결과로 간접 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerNodeServiceTest {

    @Mock
    private ServerNodeRepository serverNodeRepository;

    @Mock
    private BoardModelRepository boardModelRepository;

    @Mock
    private SettingRepository settingRepository;

    @Mock
    private NodeStepExecutionRepository nodeStepExecutionRepository;

    @InjectMocks
    private ServerNodeService serverNodeService;

    @Captor
    private ArgumentCaptor<List<NodeStepExecution>> executionsCaptor;

    // --- 테스트 픽스처 헬퍼 ---

    /**
     * 호환되는 BoardModel/BIOS/BMC DTO 세트를 생성하여 BasicUpdate 인스턴스를 반환한다.
     */
    private BasicUpdate createBasicUpdate() {
        BoardModelDTO boardModelDTO = new BoardModelDTO(1L, com.example.serverprovision.domain.node.model.enums.Vendor.GIGABYTE,
                "B550M-DS3H", "테스트 보드", true);
        BoardBIOSDTO biosDTO = new BoardBIOSDTO(1L, boardModelDTO, "1.0", "/bios/1.0.bin", "테스트 BIOS", true);
        BoardBMCDTO bmcDTO = new BoardBMCDTO(1L, boardModelDTO, "1.0", "/bmc/1.0.bin", "테스트 BMC", true);
        return new BasicUpdate(boardModelDTO, biosDTO, bmcDTO);
    }

    /**
     * 필드 없는 BasicSetting 스텁 인스턴스를 반환한다.
     */
    private BasicSetting createBasicSetting() {
        return new BasicSetting();
    }

    /**
     * OSSetting 인스턴스를 반환한다.
     */
    private OSSetting createOSSetting() {
        return new OSSetting("enforcing", List.of("sshd"), List.of("vim"));
    }

    /**
     * 지정된 processList를 가진 ServerSetting을 빌드한다.
     */
    private ServerSetting buildSetting(List<AbstractSettingProcess> processList) {
        SettingProcess settingProcess = new SettingProcess(processList);
        return ServerSetting.builder()
                .id(100L)
                .name("테스트 세팅")
                .settingProcess(settingProcess)
                .build();
    }

    /**
     * 기본 상태의 ServerNode를 빌드한다.
     */
    private ServerNode buildNode() {
        return ServerNode.builder()
                .id(1L)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .targetJob(JobType.IDLE)
                .status(ProvisioningStatus.NEW)
                .build();
    }

    // =========================================================================
    // assignSetting 정상 케이스
    // =========================================================================

    @Nested
    @DisplayName("assignSetting: 정상 할당")
    class AssignSettingSuccess {

        @Test
        @DisplayName("노드와 세팅이 존재하면 기존 이력 삭제 후 새 NodeStepExecution을 생성한다")
        void assignSetting_withValidNodeAndSetting_createsExecutions() {
            // given
            ServerNode node = buildNode();
            BasicUpdate basicUpdate = createBasicUpdate();
            BasicSetting basicSetting = createBasicSetting();
            ServerSetting setting = buildSetting(List.of(basicUpdate, basicSetting));

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then: 기존 이력 삭제 확인
            verify(nodeStepExecutionRepository).deleteAllByNode(node);

            // then: 새 NodeStepExecution 2개 생성 (BASIC_UPDATE + BASIC_SETTING)
            verify(nodeStepExecutionRepository).saveAll(executionsCaptor.capture());
            List<NodeStepExecution> savedExecutions = executionsCaptor.getValue();
            assertThat(savedExecutions).hasSize(2);

            // 각 execution 의 stepType 검증
            assertThat(savedExecutions)
                    .extracting(NodeStepExecution::getStepType)
                    .containsExactlyInAnyOrder(
                            SettingProcessStep.BASIC_UPDATE,
                            SettingProcessStep.BASIC_SETTING
                    );

            // 모든 execution 이 PENDING 상태로 생성되었는지 검증
            assertThat(savedExecutions)
                    .allMatch(exec -> exec.getStatus() == StepExecutionStatus.PENDING);

            // then: node 에 세팅과 targetJob 이 할당되었는지 검증
            assertThat(node.getServerSetting()).isEqualTo(setting);
            // BASIC_UPDATE 포함 → BIOS_UPDATE
            assertThat(node.getTargetJob()).isEqualTo(JobType.BIOS_UPDATE);
        }

        @Test
        @DisplayName("3개 단계(BASIC_UPDATE, BASIC_SETTING, OS_SETTING) 포함 시 3개 execution 생성")
        void assignSetting_threeSteps_createsThreeExecutions() {
            // given
            ServerNode node = buildNode();
            BasicUpdate basicUpdate = createBasicUpdate();
            BasicSetting basicSetting = createBasicSetting();
            OSSetting osSetting = createOSSetting();
            ServerSetting setting = buildSetting(List.of(basicUpdate, basicSetting, osSetting));

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then
            verify(nodeStepExecutionRepository).saveAll(executionsCaptor.capture());
            assertThat(executionsCaptor.getValue()).hasSize(3);
        }
    }

    // =========================================================================
    // assignSetting 재할당 케이스
    // =========================================================================

    @Nested
    @DisplayName("assignSetting: 세팅 재할당")
    class AssignSettingReassignment {

        @Test
        @DisplayName("이미 세팅이 있는 노드에 새 세팅을 할당하면 기존 이력 삭제 후 새 이력 생성")
        void assignSetting_reassignment_deletesOldAndCreatesNew() {
            // given: 기존 세팅이 할당된 노드
            ServerSetting oldSetting = buildSetting(List.of(createBasicUpdate()));
            ServerNode node = ServerNode.builder()
                    .id(1L)
                    .macAddress("AA:BB:CC:DD:EE:FF")
                    .targetJob(JobType.BIOS_UPDATE)
                    .status(ProvisioningStatus.IN_PROGRESS)
                    .serverSetting(oldSetting)
                    .build();

            // 새 세팅
            BasicSetting basicSetting = createBasicSetting();
            OSSetting osSetting = createOSSetting();
            ServerSetting newSetting = buildSetting(List.of(basicSetting, osSetting));

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(200L)).willReturn(Optional.of(newSetting));

            // when
            serverNodeService.assignSetting(1L, 200L);

            // then: 기존 이력 삭제 확인
            verify(nodeStepExecutionRepository).deleteAllByNode(node);

            // then: 새 이력 2개 생성
            verify(nodeStepExecutionRepository).saveAll(executionsCaptor.capture());
            assertThat(executionsCaptor.getValue()).hasSize(2);

            // then: 새 세팅으로 교체됨
            assertThat(node.getServerSetting()).isEqualTo(newSetting);
            // BASIC_SETTING + OS_SETTING 만 있으면 IDLE
            assertThat(node.getTargetJob()).isEqualTo(JobType.IDLE);
        }
    }

    // =========================================================================
    // assignSetting 예외 케이스
    // =========================================================================

    @Nested
    @DisplayName("assignSetting: 예외 케이스")
    class AssignSettingExceptions {

        @Test
        @DisplayName("존재하지 않는 nodeId 이면 IllegalArgumentException 발생")
        void assignSetting_nodeNotFound_throwsException() {
            // given
            given(serverNodeRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> serverNodeService.assignSetting(999L, 100L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("서버 노드를 찾을 수 없습니다");

            // 세팅 조회는 시도하지 않음
            verify(settingRepository, never()).findById(any());
            verify(nodeStepExecutionRepository, never()).deleteAllByNode(any());
        }

        @Test
        @DisplayName("존재하지 않는 settingId 이면 IllegalArgumentException 발생")
        void assignSetting_settingNotFound_throwsException() {
            // given
            ServerNode node = buildNode();
            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> serverNodeService.assignSetting(1L, 999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("세팅 주문서를 찾을 수 없습니다");

            // 이력 삭제는 시도하지 않음
            verify(nodeStepExecutionRepository, never()).deleteAllByNode(any());
        }
    }

    // =========================================================================
    // determineTargetJob 간접 검증 (assignSetting 결과로)
    // =========================================================================

    @Nested
    @DisplayName("determineTargetJob: 간접 검증")
    class DetermineTargetJobIndirect {

        @Test
        @DisplayName("OS_INSTALLATION 단계 포함 시 targetJob 이 OS_INSTALLATION 이다")
        void assignSetting_withOsInstallation_setsOsInstallationJob() {
            // given: OS_INSTALLATION 을 포함하는 processList
            // OSInstallation 은 생성자에 호환성 검증이 필요하므로 mock 사용
            AbstractSettingProcess osProcess = mock(AbstractSettingProcess.class);
            given(osProcess.getProcessStep()).willReturn(SettingProcessStep.OS_INSTALLATION);

            BasicUpdate basicUpdate = createBasicUpdate();

            ServerSetting setting = buildSetting(List.of(basicUpdate, osProcess));
            ServerNode node = buildNode();

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then: OS_INSTALLATION 이 최우선으로 결정됨
            assertThat(node.getTargetJob()).isEqualTo(JobType.OS_INSTALLATION);
        }

        @Test
        @DisplayName("BASIC_UPDATE 만 포함(OS_INSTALLATION 없음) 시 targetJob 이 BIOS_UPDATE 이다")
        void assignSetting_withBasicUpdateOnly_setsBiosUpdateJob() {
            // given
            BasicUpdate basicUpdate = createBasicUpdate();
            ServerSetting setting = buildSetting(List.of(basicUpdate));
            ServerNode node = buildNode();

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then
            assertThat(node.getTargetJob()).isEqualTo(JobType.BIOS_UPDATE);
        }

        @Test
        @DisplayName("BASIC_SETTING 만 포함 시 targetJob 이 IDLE 이다")
        void assignSetting_withBasicSettingOnly_setsIdleJob() {
            // given
            BasicSetting basicSetting = createBasicSetting();
            ServerSetting setting = buildSetting(List.of(basicSetting));
            ServerNode node = buildNode();

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then
            assertThat(node.getTargetJob()).isEqualTo(JobType.IDLE);
        }

        @Test
        @DisplayName("OS_SETTING 만 포함 시 targetJob 이 IDLE 이다")
        void assignSetting_withOsSettingOnly_setsIdleJob() {
            // given
            OSSetting osSetting = createOSSetting();
            ServerSetting setting = buildSetting(List.of(osSetting));
            ServerNode node = buildNode();

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then
            assertThat(node.getTargetJob()).isEqualTo(JobType.IDLE);
        }

        @Test
        @DisplayName("BASIC_UPDATE + OS_INSTALLATION 동시 포함 시 OS_INSTALLATION 이 우선한다")
        void assignSetting_withBothUpdateAndOsInstall_osInstallationWins() {
            // given
            BasicUpdate basicUpdate = createBasicUpdate();
            AbstractSettingProcess osProcess = mock(AbstractSettingProcess.class);
            given(osProcess.getProcessStep()).willReturn(SettingProcessStep.OS_INSTALLATION);

            ServerSetting setting = buildSetting(List.of(basicUpdate, osProcess));
            ServerNode node = buildNode();

            given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
            given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

            // when
            serverNodeService.assignSetting(1L, 100L);

            // then: OS_INSTALLATION 이 BIOS_UPDATE 보다 우선
            assertThat(node.getTargetJob()).isEqualTo(JobType.OS_INSTALLATION);
        }
    }

    // =========================================================================
    // deleteAllByNode 호출 순서 검증
    // =========================================================================

    @Test
    @DisplayName("deleteAllByNode 이 saveAll 보다 먼저 호출된다")
    void assignSetting_deleteBeforeSave_orderVerified() {
        // given
        ServerNode node = buildNode();
        BasicUpdate basicUpdate = createBasicUpdate();
        ServerSetting setting = buildSetting(List.of(basicUpdate));

        given(serverNodeRepository.findById(1L)).willReturn(Optional.of(node));
        given(settingRepository.findById(100L)).willReturn(Optional.of(setting));

        var inOrder = inOrder(nodeStepExecutionRepository);

        // when
        serverNodeService.assignSetting(1L, 100L);

        // then: delete 가 save 보다 먼저 호출됨
        inOrder.verify(nodeStepExecutionRepository).deleteAllByNode(node);
        inOrder.verify(nodeStepExecutionRepository).saveAll(any());
    }
}
