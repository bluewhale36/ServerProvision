package com.example.serverprovision.execution.dto;

import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import jakarta.validation.constraints.AssertTrue;

/**
 * PXE 첫 접촉 요청. iPXE 가 채워 보내는 값이라 형식이 틀리면 손으로 만든 요청이다. 형식은 VO 와 같은 규칙으로 보고, 틀리면 400 으로
 * 분류해 같은 요청이 서버 오류(스택 로그)로 쌓이지 않게 한다(E4-1-b-7 CP5 O-5). 비어 있는 값의 처리는 등록 단계가 맡는다.
 */
public record BootIPXEInfoRequest(
        String macAddress,
        String ipAddress,
        String systemUUID,
        String vendor,
        String boardModel
) {

    @AssertTrue(message = "MAC 주소 형식이 올바르지 않습니다.")
    public boolean isMacAddressWellFormed() {
        return macAddress == null || macAddress.isBlank() || MacAddressVO.isValid(macAddress);
    }

    @AssertTrue(message = "IPv4 주소 형식이 올바르지 않습니다.")
    public boolean isIpAddressWellFormed() {
        return ipAddress == null || ipAddress.isBlank() || IpAddressVO.isValid(ipAddress);
    }
}
