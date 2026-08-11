package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 목록 행 1개. 도메인 의미가 있는 값은 원시 문자열이 아니라 VO / Enum / UUID 로 전달한다(Primitive Obsession 금지).
 * <p>vendor 와 status 는 <b>도출값</b>이다(U1 §D2/§D4) — vendor 는 boardModel 에서, status 는 (progress + 회수)에서
 * 서비스 매핑 단계에 계산해 싣는다. 저장 컬럼이 아니다.</p>
 * detail / nic 가 아직 없을 수 있어 vendor / boardModelName / primaryIp 는 nullable.
 * <p>{@code currentPhase}(U3-3) — 서버별 현재 프로비저닝 단계. 목록이 단계를 열로 보여주고 phase 필터와 같은 값을
 * 쓰므로, 필터로 좁힌 결과가 왜 그렇게 나왔는지 행에서 바로 읽힌다. 진행 정보가 없으면 null.</p>
 * <p>{@code contactRemainingSeconds}(S7) — 연결 중일 때 "끊어짐 전이까지 남은 초"(브라우저 rollover
 * 재조회 예약 입력), 비연결이면 null. 기준 90초의 SSOT 는 조회 서비스다.</p>
 */
public record GuestServerSummaryResponse(
        UUID id,
        String name,
        UUID systemUuid,
        Vendor vendor,
        String boardModelName,
        GuestServerStatus status,
        ProvisioningPhase currentPhase,
        IpAddressVO primaryIp,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt,
        boolean contactActive,
        Long contactRemainingSeconds
) {
}
