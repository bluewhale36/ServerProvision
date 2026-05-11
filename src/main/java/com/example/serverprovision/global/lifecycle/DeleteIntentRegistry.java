package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenExpiredException;
import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MK3-2 (DCM3-2.6) — softDelete reject modal 의 1회용 token 보관소.
 *
 * <p>{@link com.example.serverprovision.management.common.nudge.NudgeRegistry} 와 동형 패턴 :
 * {@link ConcurrentMap} 에 5분 TTL 로 보관, {@link Scheduled} pruner 가 1분 간격 회수, JVM 재시작 시 소실.</p>
 *
 * <p>두 registry 의 동형성은 자체 진단 §10 의 인지 사항 — 후속 슬라이스에서 추상화 가능. 본 슬라이스에선
 * 별도 컴포넌트로 두어 정책 변경 (DeleteIntent) 의 평가가 nudge 인프라 안정성에 가려지지 않게 한다.</p>
 *
 * <p>분산 인스턴스 대응은 §7.4 미해결 항목 — NudgeRegistry 와 동일 가정 (단일 JVM).</p>
 */
@Slf4j
@Component
public class DeleteIntentRegistry {

    private final ConcurrentMap<DeleteIntentToken, DeleteIntent> store = new ConcurrentHashMap<>();

    /** 신규 intent 발급 + 등록. service 의 사전조건 위반 시 호출 후 응답에 token 동봉. */
    public DeleteIntent issue(ResourceType resourceType, Long resourceId, Path missingPath, boolean ghostCandidate) {
        DeleteIntent intent = DeleteIntent.issue(resourceType, resourceId, missingPath, ghostCandidate);
        store.put(intent.token(), intent);
        return intent;
    }

    /**
     * 1회용 consume — TTL 검증 후 store 에서 제거하고 반환. 만료 / 부재 / mismatch 시 명시적 예외.
     *
     * @param token  modal 두 번째 호출이 동봉한 token
     * @param expectedType  호출 endpoint 의 resourceType (mismatch 검증)
     * @param expectedId    호출 endpoint 의 resourceId (mismatch 검증)
     */
    public DeleteIntent consume(DeleteIntentToken token, ResourceType expectedType, Long expectedId) {
        DeleteIntent intent = store.remove(token);
        if (intent == null) {
            throw new DeleteIntentTokenExpiredException(token == null ? "(null)" : token.asString());
        }
        if (intent.isExpired(Instant.now())) {
            throw new DeleteIntentTokenExpiredException(token.asString());
        }
        if (!intent.matches(expectedType, expectedId)) {
            throw new DeleteIntentTokenMismatchException(
                    token.asString(),
                    intent.resourceType() + "#" + intent.resourceId(),
                    expectedType + "#" + expectedId);
        }
        return intent;
    }

    /** 외부에서 token 무효화 (사용자 명시 cancel 등). 호출 후 token 은 즉시 사용 불가. */
    public boolean invalidate(DeleteIntentToken token) {
        return store.remove(token) != null;
    }

    /** TTL pruner — 1분 간격으로 만료 token 회수. NudgeRegistry 와 동일 주기. */
    @Scheduled(fixedDelay = 60_000L)
    public void purgeExpired() {
        Instant now = Instant.now();
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().isExpired(now));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("[delete-intent] 만료 token {} 건 회수 (활성 {} 건 잔존)", removed, store.size());
        }
    }
}
