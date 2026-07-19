package com.example.serverprovision.execution.vo;

/**
 * 펌웨어·소프트웨어 인벤토리 수집 계약(E1-2) — {@code guest_server_detail.software_spec} JSON 의 앱측 구조.
 * bmcVersion 은 이번 수집 범위 밖(에이전트 미보고 시 null) — E2 의 BMC 대조 skip 판정(DEC-34) 입력으로
 * 확장될 자리다.
 */
public record SoftwareSpec(String biosVersion, String bmcVersion) {
}
