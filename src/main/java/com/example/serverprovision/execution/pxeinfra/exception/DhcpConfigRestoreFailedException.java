package com.example.serverprovision.execution.pxeinfra.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * 재기동 실패 후 이전본 복원까지 실패해 서비스가 여전히 비활성인 최악의 경우(RESTORE_FAILED). 자동 복구가
 * 불가하므로 detail 에 수동 복구 절차(이전 조각 경로·{@code systemctl restart dhcpd})를 담아 안내한다.
 *
 * <p>{@code @ResponseStatus} 없음 → {@code handleDomain} 이 500 으로 매핑(서버/인프라 측 문제).</p>
 */
public class DhcpConfigRestoreFailedException extends DomainException {

    public DhcpConfigRestoreFailedException(String detail) {
        super(detail);
    }
}
