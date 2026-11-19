package com.example.serverprovision.execution.vo;

import com.example.serverprovision.execution.enums.StartNetworkBlockKind;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 개시 네트워크 판정의 SSOT(U7). 서버 상세의 버튼 차단과 개시 409 가드가 이 함수 하나를 부른다.
 * 막는 이유를 모두 모은다. 허용 대역이 없으면 '밖' 은 계산할 수 없으므로 '모름' 만 함께 모은다.
 */
public final class StartNetworkBlocks {

    private StartNetworkBlocks() {
    }

    public static List<StartNetworkBlock> judge(Optional<ProvisioningAllowedCidr> allowedCidr,
                                                IpAddressVO bmcIp, IpAddressVO primaryNicIp) {
        List<StartNetworkBlock> blocks = new ArrayList<>();
        ProvisioningAllowedCidr range = allowedCidr.orElse(null);
        if (range == null) {
            blocks.add(new StartNetworkBlock(StartNetworkBlockKind.RANGE_UNSET, null, null));
        }
        judgeAddress(blocks, range, bmcIp, StartNetworkBlockKind.BMC_UNKNOWN, StartNetworkBlockKind.BMC_OUTSIDE);
        judgeAddress(blocks, range, primaryNicIp, StartNetworkBlockKind.LAN_UNKNOWN, StartNetworkBlockKind.LAN_OUTSIDE);
        return List.copyOf(blocks);
    }

    private static void judgeAddress(List<StartNetworkBlock> blocks, ProvisioningAllowedCidr range, IpAddressVO ip,
                                     StartNetworkBlockKind unknown, StartNetworkBlockKind outside) {
        if (ip == null) {
            blocks.add(new StartNetworkBlock(unknown, null, null));
        } else if (range != null && !range.contains(ip)) {
            blocks.add(new StartNetworkBlock(outside, ip, range));
        }
    }
}
