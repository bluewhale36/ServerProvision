package com.example.serverprovision.provisioning.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * BMC 계정 비밀번호 변경 화면 렌더 (MVC). 입력 → 전송 시 Redfish 적용 계획(경로/method/body)을 화면에 표시한다.
 * 실제 Redfish 호출은 하지 않는다 (PoC — 서버는 로그만).
 */
@Controller
@RequestMapping("/provisioning/bmc-password")
public class BmcPasswordController {

	@GetMapping
	public String bmcPassword() {
		return "provisioning/bmc-password";
	}
}
