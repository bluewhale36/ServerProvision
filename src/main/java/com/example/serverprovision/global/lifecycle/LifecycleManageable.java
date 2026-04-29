package com.example.serverprovision.global.lifecycle;

/**
 * MK2 — Deprecate / Soft-delete / Restore 가능한 엔티티가 구현해야 하는 인터페이스.
 *
 * <p>구현체 (CP3 에 추가 예정) 는 세 boolean 컬럼 ({@code is_enabled} / {@code is_deprecated} /
 * {@code is_deleted}) 을 직접 보유하고 본 메서드들이 그 boolean 을 토글한다. 본 인터페이스의 default
 * 메서드인 {@link #currentStage()} 는 두 boolean 조합을 어휘로 환산해 응답 DTO 에 노출하는 단일
 * 진입점이다 — 클라이언트가 boolean 조합을 수행하지 않도록 서버에서 계산해 내려준다.</p>
 *
 * <p>본 인터페이스는 CP2 시점의 시그니처 박제용이다. 실제 동작 구현은 CP3 (엔티티 마이그레이션) 와
 * CP4 (서비스 본체 + 통합 테스트) 에서 단계적으로 채워진다.</p>
 */
public interface LifecycleManageable {

    boolean isDeprecated();

    boolean isDeleted();

    /**
     * Active → Deprecated 전이. 이미 Deprecated 거나 SoftDeleted 인 경우 구현체에서 도메인 예외를
     * 던진다 (CP3 에 정의될 {@code IllegalDeprecationStateException}).
     */
    void deprecate();

    /**
     * Deprecated → Active 전이. Active 또는 SoftDeleted 인 경우 도메인 예외.
     */
    void undeprecate();

    /**
     * Active / Deprecated → SoftDeleted 전이. {@code is_deprecated} 는 보존된다.
     */
    void softDelete();

    /**
     * SoftDeleted → 이전 stage 복귀. {@code is_deprecated} 보존되어 있으면 Deprecated 로,
     * 아니면 Active 로 복귀.
     */
    void restore();

    default LifecycleStage currentStage() {
        return LifecycleStage.of(isDeprecated(), isDeleted());
    }
}
