package com.example.serverprovision.global.systemsetting.spi;

import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;

import java.util.Optional;

/**
 * 프로비저닝 허용 대역을 따라가지만 운영자가 다시 적용해야 반영되는 설정(HF35). 허용 대역 저장 뒤 시스템 설정 화면이
 * 이 안내를 모아 보인다. 시스템 설정이 그 설정의 도메인을 모르게 하려고 구현은 그 도메인이 한다(dnsmasq 구성 선례).
 */
public interface ProvisioningRangeFollower {

    /** 새 허용 대역이 아직 반영되지 않았으면 운영자에게 보일 안내 한 문장. */
    Optional<String> reapplyNotice(ProvisioningAllowedCidr current);
}
