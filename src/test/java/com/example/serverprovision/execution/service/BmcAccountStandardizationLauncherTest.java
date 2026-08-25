package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * E1.6 CP4 — 비동기 진입점(D-1). 위임 · 예외 삼킴(비동기 스레드에서 조용히 사라지지 않게)과
 * 배선 애노테이션(AFTER_COMMIT · fallbackExecution · @Async)을 검증한다 — phase 오기입은
 * 컴파일도 단위 실행도 통과해 버리므로 선언 자체를 고정한다.
 */
class BmcAccountStandardizationLauncherTest {

    private final BmcAccountStandardizationService service = mock(BmcAccountStandardizationService.class);
    private final BmcAccountStandardizationLauncher launcher = new BmcAccountStandardizationLauncher(service);

    @Test
    @DisplayName("이벤트 수신 — 사다리 본체로 위임한다")
    void delegates() {
        UUID id = UUID.randomUUID();

        launcher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(id));

        verify(service).standardize(id);
    }

    @Test
    @DisplayName("본체 예외는 삼키고 로그만 — 비동기 스레드의 예외는 전파처가 없다")
    void exceptionSwallowed() {
        willThrow(new IllegalStateException("boom")).given(service).standardize(org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> launcher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배선 선언 — AFTER_COMMIT · fallbackExecution · @Async 가 리스너 메서드에 고정돼 있다")
    void wiringDeclared() throws NoSuchMethodException {
        Method listener = BmcAccountStandardizationLauncher.class
                .getMethod("onBmcEndpointDiscovered", BmcEndpointDiscoveredEvent.class);

        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
        assertThat(listener.getAnnotation(Async.class)).isNotNull();
    }
}
