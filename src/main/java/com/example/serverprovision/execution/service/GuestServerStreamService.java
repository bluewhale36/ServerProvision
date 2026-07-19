package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실시간 상태 스트림(S7) — SSE 구독자(emitter) 레지스트리 + 브로드캐스트.
 *
 * <p>{@link GuestServerChangedEvent} 를 <b>AFTER_COMMIT</b> 에서만 구독한다 — 롤백된 변화를 화면에
 * 쏘는 사고를 트랜잭션 경계가 원천 차단한다(S5-13 이 이연해 둔 이 프로젝트 첫 EventListener 실사례).
 * 발행측 트랜잭션은 이미 커밋됐으므로 리스너 내부 실패는 원 작업에 영향이 없고, 화면은 다음 신호나
 * 수동 새로고침으로 회복한다(plan §7).</p>
 *
 * <p>연결 수명: 등록 → 완료/타임아웃/에러 콜백 제거 + send 실패(닫힌 연결) 즉시 제거. 재연결은
 * 브라우저 EventSource 기본 동작에 위임하므로 서버측 재연결 코드는 없다.</p>
 */
@Slf4j
@Service
public class GuestServerStreamService {

    /** 구독 타임아웃 30분 — 만료 시 EventSource 가 자동 재구독하므로 좀비 연결이 주기적으로 정리된다. */
    private static final long SUBSCRIPTION_TIMEOUT_MILLIS = 30L * 60 * 1000;

    /** 순회 중 제거가 일어나는 브로드캐스트 특성상 CopyOnWrite — 구독자는 운영자 소수 전제(plan §8). */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SUBSCRIPTION_TIMEOUT_MILLIS);
        register(emitter);
        return emitter;
    }

    /** 등록 + 수명 콜백 배선 — 테스트가 mock emitter 로 콜백·send 실패 경로를 검증하는 이음새. */
    void register(SseEmitter emitter) {
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        // 구독 직후 comment 1프레임 — 응답 스트림을 즉시 흘려 브라우저/프록시가 연결 수립을 확정하게 한다
        send(emitter, SseEmitter.event().comment("connected"));
    }

    public int subscriberCount() {
        return emitters.size();
    }

    /**
     * 커밋 확정된 변화만 전 구독자에게 신호한다. {@code fallbackExecution = true} — 트랜잭션 밖 발행
     * (테스트 등)도 수신하도록 명시.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(GuestServerChangedEvent event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("changed").data(event.serverId().toString()));
        }
    }

    /** 25초 heartbeat(comment 프레임) — 프록시 idle 절단 방지. 죽은 연결도 이때 정리된다. */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder frame) {
        try {
            emitter.send(frame);
        } catch (Exception e) {
            // 닫힌 연결(IOException/IllegalStateException) — 즉시 제거해 다음 신호가 막히지 않게 한다
            emitters.remove(emitter);
            log.debug("SSE 구독자 제거 — 연결 끊김 : {}", e.getMessage());
        }
    }
}
