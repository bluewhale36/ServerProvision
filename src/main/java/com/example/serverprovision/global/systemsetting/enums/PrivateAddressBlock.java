package com.example.serverprovision.global.systemsetting.enums;

import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;

import java.util.Arrays;
import java.util.Optional;

/** RFC 1918 내부망 블록. 프로비저닝 허용 대역은 이 셋 중 하나 안에 있어야 한다. */
public enum PrivateAddressBlock {

    CLASS_A("10.0.0.0/8"),
    CLASS_B("172.16.0.0/12"),
    CLASS_C("192.168.0.0/16");

    private final SubnetCidr block;

    PrivateAddressBlock(String cidr) {
        this.block = SubnetCidr.of(cidr);
    }

    public int prefixLength() {
        return block.prefixLength();
    }

    public boolean containsAddress(IpAddressVO address) {
        return block.contains(address);
    }

    /** 주소가 속한 블록. 어느 블록에도 없으면 empty. */
    public static Optional<PrivateAddressBlock> enclosing(IpAddressVO address) {
        return Arrays.stream(values()).filter(b -> b.containsAddress(address)).findFirst();
    }
}
