package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 목록 행 1개. 도메인 의미가 있는 값은 원시 문자열이 아니라 VO / Enum / UUID 로 전달한다(Primitive Obsession 금지).
 * <p>vendor 와 status 는 <b>도출값</b>이다(U1 §D2/§D4) — vendor 는 boardModel 에서, status 는 (progress + 회수)에서
 * 서비스 매핑 단계에 계산해 싣는다. 저장 컬럼이 아니다.</p>
 * detail / nic 가 아직 없을 수 있어 vendor / boardModelName / primaryIp 는 nullable.
 */
public record GuestServerSummaryResponse(
        UUID id,
        String name,
        UUID systemUuid,
        Vendor vendor,
        String boardModelName,
        GuestServerStatus status,
        IpAddressVO primaryIp,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt,
        boolean contactActive
) {
}
