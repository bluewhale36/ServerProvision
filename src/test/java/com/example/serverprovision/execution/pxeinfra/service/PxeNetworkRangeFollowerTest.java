package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** HF35 — dnsmasq 구성이 새 허용 대역을 아직 반영하지 않았을 때만 재적용 안내를 낸다. */
@ExtendWith(MockitoExtension.class)
class PxeNetworkRangeFollowerTest {

    @Mock PxeNetworkConfigService configService;
    @InjectMocks PxeNetworkRangeFollower follower;

    @Test
    @DisplayName("저장된 서브넷과 새 대역이 다르면 안내")
    void differs_notice() {
        given(configService.load()).willReturn(Optional.of(PxeNetworkConfigFixtures.proxy()));   // 10.1.1.0/24
        assertThat(follower.reapplyNotice(ProvisioningAllowedCidr.parse("10.1.2.0/24")))
                .contains(PxeNetworkRangeFollower.NOTICE);
    }

    @Test
    @DisplayName("같으면 안내 없음 · 구성이 아직 없으면 안내 없음(처음 저장 때 대역이 채워진다)")
    void sameOrUnsaved_noNotice() {
        given(configService.load()).willReturn(Optional.of(PxeNetworkConfigFixtures.proxy()));
        assertThat(follower.reapplyNotice(ProvisioningAllowedCidr.parse("10.1.1.0/24"))).isEmpty();

        given(configService.load()).willReturn(Optional.empty());
        assertThat(follower.reapplyNotice(ProvisioningAllowedCidr.parse("10.1.1.0/24"))).isEmpty();
    }
}
