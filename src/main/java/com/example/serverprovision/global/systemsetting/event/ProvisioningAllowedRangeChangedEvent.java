package com.example.serverprovision.global.systemsetting.event;

import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;

/**
 * 프로비저닝 허용 대역을 저장했다(HF35). 이 값을 함께 쓰는 곳(게스트 채널 출발지 제한)이 저장 커밋 뒤 새 값으로 바꾼다.
 */
public record ProvisioningAllowedRangeChangedEvent(ProvisioningAllowedCidr cidr) {
}
