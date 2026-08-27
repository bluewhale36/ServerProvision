package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 운영자 재시도 가능 판정(E2-1-b CP5 결함 F-1 정정) — 화면의 버튼 노출과 서버 409 가드가 <b>이 한
 * 지점</b>을 함께 호출한다(UI 차단 조건 = 서버 가드 조건).
 *
 * <p>왜 도메인 메서드가 아니라 별도 컴포넌트인가 — 차단 여부는 "커서가 펌웨어 step 인가" 만으로는
 * 판정할 수 없기 때문이다. 펌웨어 flash 실패를 막는 이유는 원인 미상 재-flash 가 장비를 못 쓰게 만들 수
 * 있어서인데(DEC-4), 자원 결손 시한 만료(E2-1-b)는 <b>flash 를 한 번도 시도하지 않은 실패</b>다. 둘을
 * 가르는 사실은 원장에 있고(실패 행의 origin), 엔티티는 원장을 모른다.</p>
 *
 * <p>이 구분이 없으면 이 슬라이스의 약속이 끊긴다 — 자원을 되살린 운영자에게 화면상 복귀 경로가
 * 사라지기 때문이다(CP5 A6 에서 실측).</p>
 */
@Component
@RequiredArgsConstructor
public class RetryPolicy {

    private final ProvisioningHistoryRepository provisioningHistoryRepository;

    /** 화면 · 가드 공용 진입점. 원장을 이미 들고 있는 호출자는 아래 오버로드로 재조회를 피한다. */
    public boolean isBlocked(ProvisioningProgress progress) {
        if (!blockCandidate(progress)) {
            return false;
        }
        return isBlocked(progress,
                provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(progress.getGuestServer().getId()));
    }

    public boolean isBlocked(ProvisioningProgress progress, List<ProvisioningHistory> history) {
        // 굽지 않은 실패(자원 결손 시한 만료)와 다 구운 뒤의 실패(복귀 시한 만료)는 벽돌 리스크가 없다 —
        // 후자는 모든 축이 닫힌 뒤라 재시도가 굽기를 다시 열지 않고 전원 · 반영 확인만 잇는다(2026-08-27 실기).
        return blockCandidate(progress) && !isRetrySafeFailure(progress, history);
    }

    public boolean isRetryable(ProvisioningProgress progress, List<ProvisioningHistory> history) {
        return progress != null && progress.isFailed() && !isBlocked(progress, history);
    }

    /** 차단 후보 — 실패했고 그 지점이 펌웨어 flash step 이다(커서 = 실패 지점, ES-2 D-5). */
    private boolean blockCandidate(ProvisioningProgress progress) {
        return progress != null && progress.isFailed() && progress.isFirmwarePhaseFailure();
    }

    /** 그 실패가 결손 대기 시한 만료 또는 복귀 시한 만료였는가 — 사건 시점에 원장이 적어 둔 사실을 읽는다. */
    private boolean isRetrySafeFailure(ProvisioningProgress progress, List<ProvisioningHistory> history) {
        return history.stream()
                .filter(row -> row.getStatus() == ProvisioningStatus.FAILED)
                .filter(row -> progress.getFailedAt().equals(row.getFinishedAt()))
                .anyMatch(row -> row.isHoldTtlOrigin()
                        || FlashLedger.RETURN_TIMEOUT.equals(row.flashFailureReason()));
    }
}
