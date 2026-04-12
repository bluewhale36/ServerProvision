package com.example.serverprovision.domain.provisioning.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.node.entity.NodeStepExecution;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import com.example.serverprovision.domain.node.repository.NodeStepExecutionRepository;
import com.example.serverprovision.domain.provisioning.model.strategy.ProvisioningStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * {@link ProvisioningScriptService} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ProvisioningScriptServiceTest {

    @Mock
    private NodeStepExecutionRepository nodeStepExecutionRepository;

    @Mock
    private ProvisioningStrategy osStrategy;

    @Mock
    private ProvisioningStrategy basicUpdateStrategy;

    @InjectMocks
    private ProvisioningScriptService service;

    /**
     * @InjectMocks 로는 List<ProvisioningStrategy> 주입이 안 되므로
     * 수동으로 strategies 리스트를 세팅하는 헬퍼.
     */
    private void injectStrategies(ProvisioningStrategy... strategies) {
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "strategies", List.of(strategies));
    }

    // --- 세팅 미할당 케이스 ---

    @Test
    @DisplayName("ServerSetting이 null이면 로컬 부팅 스크립트를 반환한다")
    void generateIPXEScript_noSetting_returnsLocalBoot() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(null);

        injectStrategies(osStrategy);

        // when
        String script = service.generateIPXEScript(node);

        // then
        assertThat(script).contains("#!ipxe");
        assertThat(script).contains("sanboot");
        verifyNoInteractions(nodeStepExecutionRepository);
    }

    @Test
    @DisplayName("SettingProcess가 null이면 로컬 부팅 스크립트를 반환한다")
    void generateIPXEScript_nullSettingProcess_returnsLocalBoot() {
        // given
        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(null);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        injectStrategies(osStrategy);

        // when
        String script = service.generateIPXEScript(node);

        // then
        assertThat(script).contains("#!ipxe");
        assertThat(script).contains("sanboot");
    }

    // --- PENDING 단계 없음 ---

    @Test
    @DisplayName("PENDING 단계가 없으면 로컬 부팅 스크립트를 반환한다")
    void generateIPXEScript_noPendingStep_returnsLocalBoot() {
        // given
        AbstractSettingProcess process = mock(AbstractSettingProcess.class);
        SettingProcess settingProcess = new SettingProcess(List.of(process));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.empty());

        injectStrategies(osStrategy);

        // when
        String script = service.generateIPXEScript(node);

        // then
        assertThat(script).contains("sanboot");
    }

    // --- 올바른 전략 매칭 ---

    @Test
    @DisplayName("OSInstallation 프로세스에 매칭되는 전략으로 스크립트를 생성한다")
    void generateIPXEScript_osInstallationProcess_matchesCorrectStrategy() {
        // given
        AbstractSettingProcess osProcess = mock(AbstractSettingProcess.class);
        when(osProcess.getProcessStep()).thenReturn(SettingProcessStep.OS_INSTALLATION);

        SettingProcess settingProcess = new SettingProcess(List.of(osProcess));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        NodeStepExecution nextStep = mock(NodeStepExecution.class);
        when(nextStep.getStepType()).thenReturn(SettingProcessStep.OS_INSTALLATION);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.of(nextStep));

        // osStrategy supports 이 프로세스
        given(osStrategy.supports(osProcess)).willReturn(true);
        given(osStrategy.generateIPXEScript(node, osProcess)).willReturn("#!ipxe\nkernel ...\nboot\n");

        // basicUpdateStrategy 는 이 프로세스를 지원하지 않음
        given(basicUpdateStrategy.supports(osProcess)).willReturn(false);

        injectStrategies(basicUpdateStrategy, osStrategy);

        // when
        String script = service.generateIPXEScript(node);

        // then
        assertThat(script).contains("#!ipxe");
        assertThat(script).contains("kernel");
        verify(osStrategy).generateIPXEScript(node, osProcess);
        verify(basicUpdateStrategy, never()).generateIPXEScript(any(), any());
    }

    // --- 매칭되는 전략 없음 ---

    @Test
    @DisplayName("매칭되는 전략이 없으면 IllegalArgumentException 발생")
    void generateIPXEScript_noMatchingStrategy_throwsException() {
        // given
        AbstractSettingProcess unknownProcess = mock(AbstractSettingProcess.class);
        when(unknownProcess.getProcessStep()).thenReturn(SettingProcessStep.BASIC_SETTING);

        SettingProcess settingProcess = new SettingProcess(List.of(unknownProcess));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        NodeStepExecution nextStep = mock(NodeStepExecution.class);
        when(nextStep.getStepType()).thenReturn(SettingProcessStep.BASIC_SETTING);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.of(nextStep));

        given(osStrategy.supports(unknownProcess)).willReturn(false);
        given(basicUpdateStrategy.supports(unknownProcess)).willReturn(false);

        injectStrategies(osStrategy, basicUpdateStrategy);

        // when & then
        assertThatThrownBy(() -> service.generateIPXEScript(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 프로세스 타입");
    }

    // --- 여러 전략 중 올바른 전략 선택 ---

    @Test
    @DisplayName("여러 전략이 등록되어 있을 때 supports()가 true인 첫 번째 전략이 선택된다")
    void generateIPXEScript_multipleStrategies_selectsFirstMatching() {
        // given
        AbstractSettingProcess buProcess = mock(AbstractSettingProcess.class);
        when(buProcess.getProcessStep()).thenReturn(SettingProcessStep.BASIC_UPDATE);

        SettingProcess settingProcess = new SettingProcess(List.of(buProcess));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        NodeStepExecution nextStep = mock(NodeStepExecution.class);
        when(nextStep.getStepType()).thenReturn(SettingProcessStep.BASIC_UPDATE);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.of(nextStep));

        // osStrategy 는 지원 안 함
        given(osStrategy.supports(buProcess)).willReturn(false);
        // basicUpdateStrategy 가 지원
        given(basicUpdateStrategy.supports(buProcess)).willReturn(true);
        given(basicUpdateStrategy.generateIPXEScript(node, buProcess))
                .willReturn("#!ipxe\nexit\n");

        injectStrategies(osStrategy, basicUpdateStrategy);

        // when
        String script = service.generateIPXEScript(node);

        // then
        assertThat(script).contains("exit");
        verify(basicUpdateStrategy).generateIPXEScript(node, buProcess);
        verify(osStrategy, never()).generateIPXEScript(any(), any());
    }

    // --- markInProgress 검증 ---

    @Test
    @DisplayName("스크립트 생성 시 해당 단계를 IN_PROGRESS로 마킹한다")
    void generateIPXEScript_marksStepInProgress() {
        // given
        AbstractSettingProcess process = mock(AbstractSettingProcess.class);
        when(process.getProcessStep()).thenReturn(SettingProcessStep.OS_INSTALLATION);

        SettingProcess settingProcess = new SettingProcess(List.of(process));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        NodeStepExecution nextStep = mock(NodeStepExecution.class);
        when(nextStep.getStepType()).thenReturn(SettingProcessStep.OS_INSTALLATION);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.of(nextStep));

        given(osStrategy.supports(process)).willReturn(true);
        given(osStrategy.generateIPXEScript(node, process)).willReturn("#!ipxe\nboot\n");

        injectStrategies(osStrategy);

        // when
        service.generateIPXEScript(node);

        // then
        verify(nextStep).markInProgress();
    }

    // --- stepType 미매칭 시 예외 ---

    @Test
    @DisplayName("실행 이력의 stepType이 프로세스 목록에 없으면 IllegalStateException 발생")
    void generateIPXEScript_stepTypeMismatch_throwsException() {
        // given
        AbstractSettingProcess process = mock(AbstractSettingProcess.class);
        when(process.getProcessStep()).thenReturn(SettingProcessStep.OS_INSTALLATION);

        SettingProcess settingProcess = new SettingProcess(List.of(process));

        ServerSetting setting = mock(ServerSetting.class);
        when(setting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(setting);

        // 실행 이력에는 BASIC_UPDATE 단계가 있지만 프로세스 목록에는 OS_INSTALLATION만 있음
        NodeStepExecution nextStep = mock(NodeStepExecution.class);
        when(nextStep.getStepType()).thenReturn(SettingProcessStep.BASIC_UPDATE);

        given(nodeStepExecutionRepository.findFirstByNodeAndStatusOrderByStepOrderAsc(
                eq(node), eq(StepExecutionStatus.PENDING)))
                .willReturn(Optional.of(nextStep));

        injectStrategies(osStrategy);

        // when & then
        assertThatThrownBy(() -> service.generateIPXEScript(node))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("세팅 프로세스 목록에 없습니다");
    }
}
