package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.domain.node.entity.ServerNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BIOS/BMC 펌웨어 업데이트 단계의 iPXE 스크립트를 생성하는 {@link ProvisioningStrategy} 구현체이다.
 *
 * <p>역할: {@link BasicUpdate} 타입의 세팅 프로세스를 지원한다.
 * 현재는 벤더별 BIOS/BMC 업데이트 로직이 미구현이므로, 안전하게 로컬 디스크로 부팅하는
 * 스텁 스크립트를 반환한다.</p>
 *
 * <p>확장 가이드: 벤더별 펌웨어 업데이트 iPXE 스크립트가 준비되면,
 * {@link BasicUpdate#getBoardModel()}에서 벤더 정보를 추출하여
 * 벤더별 분기 로직을 구현한다.</p>
 */
@Slf4j
@Component
public class BasicUpdateStrategy implements ProvisioningStrategy {

    /**
     * {@link BasicUpdate} 타입의 프로세스만 지원한다.
     *
     * @param process 검사할 세팅 프로세스
     * @return {@code true}이면 이 전략이 해당 프로세스를 처리할 수 있음
     */
    @Override
    public boolean supports(AbstractSettingProcess process) {
        return process instanceof BasicUpdate;
    }

    /**
     * BIOS/BMC 업데이트를 위한 iPXE 스크립트를 생성한다.
     *
     * <p>현재 미구현 상태이므로 안전하게 로컬 디스크로 부팅하는 스크립트를 반환한다.
     * 5초 대기 후 iPXE exit 를 호출하여 다음 부팅 장치(로컬 디스크)로 넘어간다.</p>
     *
     * @param node    대상 서버 노드
     * @param process BIOS/BMC 업데이트 프로세스 ({@link BasicUpdate}으로 캐스팅 가능)
     * @return iPXE 스크립트 문자열
     */
    @Override
    public String generateIPXEScript(ServerNode node, AbstractSettingProcess process) {
        BasicUpdate basicUpdate = (BasicUpdate) process;

        log.info("[BasicUpdateStrategy] BIOS/BMC 업데이트 미구현 — 로컬 부팅 스크립트 반환. nodeId={}, boardModel={}",
                node.getId(), basicUpdate.getBoardModel());

        // TODO: 벤더별 BIOS/BMC 펌웨어 업데이트 iPXE 스크립트 구현 필요
        return """
               #!ipxe
               echo BIOS/BMC update is not yet implemented. Booting from local disk...
               sleep 5
               exit
               """;
    }
}
