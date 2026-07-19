package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * S7 CP4 — {@link GuestServerStreamService} 단위 테스트. emitter 레지스트리 수명(등록·콜백 제거·send
 * 실패 즉시 제거)과 브로드캐스트(changed 신호·heartbeat comment)를 검증한다. mock emitter 는
 * {@code register} 이음새로 주입한다 — 실 {@link SseEmitter} 는 컨테이너 밖에서 send 실패를 못 일으킨다.
 */
class GuestServerStreamServiceTest {

    private final GuestServerStreamService service = new GuestServerStreamService();

    /** 캡처한 frame 들을 SSE 원문으로 평탄화 — "event:changed" / ":ping" 같은 와이어 포맷을 그대로 확인한다. */
    private static String wireText(SseEmitter emitter, int expectedSends) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(expectedSends)).send(captor.capture());
        return captor.getAllValues().stream()
                .flatMap(frame -> frame.build().stream())
                .map(part -> part.getData().toString())
                .collect(Collectors.joining());
    }

    @Test
    @DisplayName("subscribe — emitter 생성 + 레지스트리 등록")
    void subscribe_registers() {
        SseEmitter emitter = service.subscribe();

        assertThat(emitter).isNotNull();
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("완료·타임아웃 콜백 — 레지스트리에서 제거")
    void lifecycleCallbacks_remove() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        service.register(first);
        service.register(second);
        assertThat(service.subscriberCount()).isEqualTo(2);

        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        verify(first).onCompletion(onCompletion.capture());
        onCompletion.getValue().run();
        assertThat(service.subscriberCount()).isEqualTo(1);

        ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
        verify(second).onTimeout(onTimeout.capture());
        onTimeout.getValue().run();
        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("에러 콜백 — 레지스트리에서 제거")
    void errorCallback_removes() {
        SseEmitter emitter = mock(SseEmitter.class);
        service.register(emitter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> onError = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(onError.capture());
        onError.getValue().accept(new IOException("broken pipe"));

        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("onChanged — 전 구독자에게 event:changed + 서버 id 브로드캐스트")
    void onChanged_broadcastsToAll() throws IOException {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        service.register(first);
        service.register(second);
        UUID serverId = UUID.randomUUID();

        service.onChanged(new GuestServerChangedEvent(serverId));

        // 각 emitter: 등록 comment 1 + changed 1 = 2회. 와이어 포맷에 이벤트명·id 가 실려야 한다.
        assertThat(wireText(first, 2)).contains("event:changed").contains(serverId.toString());
        assertThat(wireText(second, 2)).contains("event:changed").contains(serverId.toString());
    }

    @Test
    @DisplayName("onChanged — send 실패 emitter 즉시 제거, 나머지 구독자는 계속 수신")
    void onChanged_removesDeadEmitter_othersContinue() throws IOException {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);
        service.register(dead);
        service.register(alive);
        willThrow(new IOException("connection closed"))
                .given(dead).send(any(SseEmitter.SseEventBuilder.class));

        service.onChanged(new GuestServerChangedEvent(UUID.randomUUID()));

        assertThat(service.subscriberCount()).isEqualTo(1);   // dead 만 제거
        assertThat(wireText(alive, 2)).contains("event:changed");

        // 제거 후 다음 신호 — dead 는 더 이상 send 대상이 아니다
        service.onChanged(new GuestServerChangedEvent(UUID.randomUUID()));
        verify(dead, times(2)).send(any(SseEmitter.SseEventBuilder.class));   // 등록 comment + 실패 1회뿐
    }

    @Test
    @DisplayName("heartbeat — 전 구독자에게 comment 프레임(ping) 송신")
    void heartbeat_sendsComment() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        service.register(emitter);

        service.heartbeat();

        assertThat(wireText(emitter, 2)).contains(":ping");
    }
}
