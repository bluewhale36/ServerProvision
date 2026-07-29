package com.example.serverprovision.execution.pxeinfra.vo;

import com.example.serverprovision.execution.vo.IpAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-I-3-c — 서브넷 CIDR VO 검증. 파싱(네트워크 주소·prefix)·넷마스크 결정적 변환·contains/containsRange 술어와
 * 잘못된 CIDR·IPv6 거절을 다룬다. contains/containsRange 는 엔티티 정적 팩토리·Validator 가 공유하는 판정 SSOT 라
 * 경계값까지 못 박는다.
 */
class SubnetCidrTest {

    // ── 파싱 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("파싱 — 네트워크 주소·prefix 를 분해한다")
    void parses_networkAddressAndPrefix() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");

        assertThat(cidr.networkAddress()).isEqualTo("10.0.2.0");
        assertThat(cidr.prefixLength()).isEqualTo(24);
        assertThat(cidr.value()).isEqualTo("10.0.2.0/24");
    }

    @Test
    @DisplayName("파싱 — 공백은 다듬는다")
    void parses_trimmed() {
        assertThat(SubnetCidr.of("  10.0.2.0/24  ").value()).isEqualTo("10.0.2.0/24");
    }

    // ── 넷마스크 결정적 변환 ──────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0/0,     0.0.0.0",
            "10.0.0.0/8,    255.0.0.0",
            "10.0.0.0/16,   255.255.0.0",
            "10.0.2.0/24,   255.255.255.0",
            "10.0.2.0/25,   255.255.255.128",
            "10.0.2.0/30,   255.255.255.252",
            "10.0.2.5/32,   255.255.255.255"
    })
    @DisplayName("넷마스크 — prefix 를 점-구분 마스크로 결정적 변환")
    void netmask_deterministic(String cidr, String expectedMask) {
        assertThat(SubnetCidr.of(cidr).netmask()).isEqualTo(expectedMask);
    }

    // ── contains ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("contains — 서브넷 안/밖과 경계(네트워크·브로드캐스트) 주소")
    void contains_membershipAndBoundaries() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");

        assertThat(cidr.contains(IpAddressVO.of("10.0.2.100"))).isTrue();
        assertThat(cidr.contains(IpAddressVO.of("10.0.2.0"))).isTrue();    // 네트워크 경계
        assertThat(cidr.contains(IpAddressVO.of("10.0.2.255"))).isTrue();  // 브로드캐스트 경계
        assertThat(cidr.contains(IpAddressVO.of("10.0.3.0"))).isFalse();   // 바로 밖
        assertThat(cidr.contains(IpAddressVO.of("10.0.1.255"))).isFalse(); // 바로 밖(아래)
    }

    @Test
    @DisplayName("contains — /0 은 모든 IPv4 를 포함")
    void contains_slashZeroIncludesAll() {
        SubnetCidr any = SubnetCidr.of("0.0.0.0/0");
        assertThat(any.contains(IpAddressVO.of("8.8.8.8"))).isTrue();
        assertThat(any.contains(IpAddressVO.of("192.168.0.1"))).isTrue();
    }

    // ── containsRange ────────────────────────────────────────────────────────

    @Test
    @DisplayName("containsRange — 두 주소 모두 안이고 start ≤ end 면 true")
    void containsRange_happy() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");
        assertThat(cidr.containsRange(IpAddressVO.of("10.0.2.100"), IpAddressVO.of("10.0.2.200"))).isTrue();
    }

    @Test
    @DisplayName("containsRange — start == end(단일 주소)면 true")
    void containsRange_singleAddress() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");
        assertThat(cidr.containsRange(IpAddressVO.of("10.0.2.50"), IpAddressVO.of("10.0.2.50"))).isTrue();
    }

    @Test
    @DisplayName("containsRange — start > end 면 false")
    void containsRange_startAfterEnd() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");
        assertThat(cidr.containsRange(IpAddressVO.of("10.0.2.200"), IpAddressVO.of("10.0.2.100"))).isFalse();
    }

    @Test
    @DisplayName("containsRange — 끝 주소가 서브넷 밖이면 false")
    void containsRange_endOutside() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");
        assertThat(cidr.containsRange(IpAddressVO.of("10.0.2.100"), IpAddressVO.of("10.0.3.5"))).isFalse();
    }

    @Test
    @DisplayName("containsRange — 시작 주소가 서브넷 밖이면 false")
    void containsRange_startOutside() {
        SubnetCidr cidr = SubnetCidr.of("10.0.2.0/24");
        assertThat(cidr.containsRange(IpAddressVO.of("10.0.1.250"), IpAddressVO.of("10.0.2.100"))).isFalse();
    }

    // ── 거절 ────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "10.0.2.0",        // 슬래시 없음
            "10.0.2.0/24/8",   // 슬래시 둘
            "10.0.2.0/ab",     // prefix 비정수
            "10.0.2.0/33",     // prefix 범위 초과
            "10.0.2.0/-1",     // prefix 음수
            "10.0.2.5/24",     // host bit 이 0 이 아님(네트워크 경계 아님)
            "fe80::/64",       // IPv6 — IpAddressVO 가 거절
            "999.1.1.0/24"     // 잘못된 옥텟
    })
    @DisplayName("거절 — 형식/범위/경계/IPv6 위반은 IllegalArgumentException")
    void rejects_invalidCidr(String raw) {
        assertThatThrownBy(() -> SubnetCidr.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("거절 — null/빈 값")
    void rejects_nullOrBlank() {
        assertThatThrownBy(() -> SubnetCidr.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubnetCidr.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
