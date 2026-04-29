package com.example.serverprovision.domain.provisioning.service;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.domain.node.entity.NodeStepExecution;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import com.example.serverprovision.domain.node.repository.NodeStepExecutionRepository;
import com.example.serverprovision.domain.provisioning.model.strategy.ProvisioningStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningScriptService {

    private final List<ProvisioningStrategy> strategies;
    private final NodeStepExecutionRepository nodeStepExecutionRepository;

    @Transactional
    public String generateIPXEScript(ServerNode node) {
        // 할당된 세팅이 없는 경우 로컬 디스크로 부팅
        if (node.getServerSetting() == null || node.getServerSetting().getSettingProcess() == null) {
            log.info("[ProvisioningScriptService] 세팅 미할당. 로컬 부팅. nodeId={}", node.getId());
            return generateLocalBootScript();
        }

        List<AbstractSettingProcess> processes = node.getServerSetting().getSettingProcess().processList();

        // PENDING 단계 중 step_order 가장 낮은 것 조회
        NodeStepExecution nextStep = nodeStepExecutionRepository
                .findFirstByNodeAndStatusOrderByStepOrderAsc(node, StepExecutionStatus.PENDING)
                .orElse(null);

        // PENDING 단계 없음 → 모든 단계 완료 또는 이력 없음
        if (nextStep == null) {
            log.info("[ProvisioningScriptService] PENDING 단계 없음. 로컬 부팅. nodeId={}", node.getId());
            return generateLocalBootScript();
        }

        log.info("[ProvisioningScriptService] 다음 실행 단계 결정. nodeId={}, stepType={}, stepOrder={}",
                node.getId(), nextStep.getStepType(), nextStep.getStepOrder());

        // processList에서 stepType으로 매칭 (배열 인덱스 무관)
        AbstractSettingProcess currentProcess = processes.stream()
                .filter(p -> p.getProcessStep() == nextStep.getStepType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "실행 이력에 등록된 단계가 세팅 프로세스 목록에 없습니다. stepType=" + nextStep.getStepType()));

        // 해당 단계를 IN_PROGRESS로 마킹
        nextStep.markInProgress();
        log.info("[ProvisioningScriptService] 단계 상태 IN_PROGRESS 전이. stepType={}", nextStep.getStepType());

        ProvisioningStrategy selectedStrategy = strategies.stream()
                .filter(strategy -> strategy.supports(currentProcess))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 프로세스 타입입니다: " + currentProcess.getClass().getSimpleName()));

        return selectedStrategy.generateIPXEScript(node, currentProcess);
    }

    private String generateLocalBootScript() {
        return """
               #!ipxe
               echo [Provisioning Portal] Booting from Local Disk...
               sanboot --no-describe --drive 0x80
               """;
    }
}
