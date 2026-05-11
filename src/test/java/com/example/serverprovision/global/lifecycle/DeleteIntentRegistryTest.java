package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenExpiredException;
import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MK3-2 (P 카테고리) — DeleteIntentRegistry 의 issue / consume / TTL / mismatch / invalidate / purge 검증.
 */
class DeleteIntentRegistryTest {

    private DeleteIntentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeleteIntentRegistry();
    }

    @Test
    @DisplayName("P1 : issue → consume → 1회용 검증")
    void issue_consume_oneShot() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), false);
        assertThat(intent).isNotNull();

        DeleteIntent consumed = registry.consume(intent.token(), ResourceType.OS_ISO, 42L);
        assertThat(consumed).isEqualTo(intent);

        // 두 번째 consume → 410 (이미 store 에서 제거됨)
        assertThatThrownBy(() -> registry.consume(intent.token(), ResourceType.OS_ISO, 42L))
                .isInstanceOf(DeleteIntentTokenExpiredException.class);
    }

    @Test
    @DisplayName("P2 : 만료 시간 경과 시 410")
    void consume_expired() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), false);
        // 강제 만료 — store 안의 entry 를 expired 인스턴스로 교체
        @SuppressWarnings("unchecked")
        ConcurrentMap<DeleteIntentToken, DeleteIntent> store =
                (ConcurrentMap<DeleteIntentToken, DeleteIntent>) ReflectionTestUtils.getField(registry, "store");
        DeleteIntent expired = new DeleteIntent(
                intent.token(), intent.resourceType(), intent.resourceId(), intent.missingPath(),
                intent.ghostCandidate(), Instant.now().minusSeconds(600), Instant.now().minusSeconds(1));
        store.put(intent.token(), expired);

        assertThatThrownBy(() -> registry.consume(intent.token(), ResourceType.OS_ISO, 42L))
                .isInstanceOf(DeleteIntentTokenExpiredException.class);
    }

    @Test
    @DisplayName("P3 : 다른 resourceId 로 consume → mismatch 거절 (410)")
    void consume_mismatch() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), false);

        assertThatThrownBy(() -> registry.consume(intent.token(), ResourceType.OS_ISO, 99L))
                .isInstanceOf(DeleteIntentTokenMismatchException.class);
    }

    @Test
    @DisplayName("P3-2 : 다른 resourceType 으로 consume → mismatch 거절")
    void consume_typeMismatch() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), false);

        assertThatThrownBy(() -> registry.consume(intent.token(), ResourceType.BIOS_BUNDLE, 42L))
                .isInstanceOf(DeleteIntentTokenMismatchException.class);
    }

    @Test
    @DisplayName("P4 : invalidate → consume → 410 (1회용 + 명시적 무효화)")
    void invalidate_thenConsume() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), false);
        boolean removed = registry.invalidate(intent.token());
        assertThat(removed).isTrue();

        assertThatThrownBy(() -> registry.consume(intent.token(), ResourceType.OS_ISO, 42L))
                .isInstanceOf(DeleteIntentTokenExpiredException.class);
    }

    @Test
    @DisplayName("P5 : purgeExpired — 만료 token 일괄 회수")
    void purgeExpired_removesExpired() {
        DeleteIntent fresh = registry.issue(ResourceType.OS_ISO, 1L, Path.of("/opt/iso/a.iso"), false);
        DeleteIntent expired = registry.issue(ResourceType.OS_ISO, 2L, Path.of("/opt/iso/b.iso"), false);
        // expired 강제 만료
        @SuppressWarnings("unchecked")
        ConcurrentMap<DeleteIntentToken, DeleteIntent> store =
                (ConcurrentMap<DeleteIntentToken, DeleteIntent>) ReflectionTestUtils.getField(registry, "store");
        store.put(expired.token(), new DeleteIntent(
                expired.token(), expired.resourceType(), expired.resourceId(), expired.missingPath(),
                expired.ghostCandidate(), Instant.now().minusSeconds(600), Instant.now().minusSeconds(1)));

        registry.purgeExpired();

        assertThat(store).containsKey(fresh.token());
        assertThat(store).doesNotContainKey(expired.token());
    }

    @Test
    @DisplayName("ghostCandidate 플래그가 token 메타에 보존됨")
    void issue_ghostCandidatePreserved() {
        DeleteIntent intent = registry.issue(ResourceType.OS_ISO, 42L, Path.of("/opt/iso/foo.iso"), true);
        assertThat(intent.ghostCandidate()).isTrue();
    }

    @Test
    @DisplayName("token 직렬화 — del-<uuid> 형식 + parse round-trip")
    void token_serialization() {
        DeleteIntentToken t1 = DeleteIntentToken.issue();
        String s = t1.asString();
        assertThat(s).startsWith("del-");
        DeleteIntentToken t2 = DeleteIntentToken.parse(s);
        assertThat(t2).isEqualTo(t1);
    }

    @Test
    @DisplayName("token parse — 잘못된 형식 → IllegalArgumentException")
    void token_parseInvalid() {
        assertThatThrownBy(() -> DeleteIntentToken.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** invariant — Map.of 사용 검증 (regression 보호용 더미). */
    @Test
    void mapInvariant() {
        Map<String, String> m = Map.of("k", "v");
        assertThat(m).containsEntry("k", "v");
    }
}
