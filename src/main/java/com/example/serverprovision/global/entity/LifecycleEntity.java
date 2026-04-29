package com.example.serverprovision.global.entity;

import com.example.serverprovision.global.lifecycle.LifecycleManageable;
import com.example.serverprovision.global.lifecycle.exception.IllegalDeprecationStateException;
import com.example.serverprovision.global.lifecycle.exception.IllegalLifecycleTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * MK2 — 운영자가 직접 관리하는 자원 (BIOS / BMC / Subprogram / OS / ISO) 의 lifecycle 공통 super.
 *
 * <p>{@code created_at} / {@code updated_at} (JPA Auditing) + lifecycle 4 boolean
 * ({@code is_enabled} / {@code is_deprecated} / {@code is_deleted}) + {@code deprecated_at} timestamp 와
 * {@link LifecycleManageable} 메서드 (가드 포함) 를 모은다. 5 엔티티가 본 super 를 상속하면서 코드 중복을
 * 제거한다 (CLAUDE.md §코딩 스타일 §중복된 코드와 가독성이 떨어지는 코드는 절대 지양 정합).</p>
 *
 * <p>{@link BaseTimeEntity} 가 별도 존재하나 본 클래스는 그것을 상속하지 않는다 — {@link BaseTimeEntity} 가
 * SuperBuilder 가 아니라 본 클래스의 {@code @SuperBuilder} 와 호환되지 않기 때문. 본 클래스가 자체 audit 필드를
 * 직접 보유한다 (created_at / updated_at 같은 컬럼).</p>
 *
 * <p><b>엔티티별 가드 메시지가 다른 경우</b> — sub-class 가 {@link #resourceLabel()} 만 override 하면 본 super 의
 * 가드 메시지가 자동으로 도메인별 라벨로 매핑된다. boolean 토글 + 가드 로직 자체는 한 곳에서만 관리된다.</p>
 */
@Getter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public abstract class LifecycleEntity implements LifecycleManageable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "is_deprecated", nullable = false)
    @Builder.Default
    private boolean isDeprecated = false;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    /**
     * 가드 메시지에 노출할 자원 라벨. sub-class 가 도메인 명칭으로 override (예: "BIOS", "BMC", "ISO").
     * 기본값은 클래스 simple name 이라 override 안 해도 동작 가능.
     */
    protected String resourceLabel() {
        return getClass().getSimpleName();
    }

    /**
     * 자원 식별자 — 가드 메시지에 노출용. sub-class 가 자기 PK 를 반환.
     */
    protected abstract Long resourceId();

    public void toggleEnabled() {
        this.isEnabled = !this.isEnabled;
    }

    @Override
    public void softDelete() {
        if (this.isDeleted) {
            throw new IllegalLifecycleTransitionException(
                    "이미 삭제된 " + resourceLabel() + " 입니다 : " + resourceId());
        }
        this.isDeleted = true;
    }

    @Override
    public void restore() {
        if (!this.isDeleted) {
            throw new IllegalLifecycleTransitionException(
                    "삭제 상태가 아닌 " + resourceLabel() + " 는 복구할 수 없습니다 : " + resourceId());
        }
        this.isDeleted = false;
    }

    @Override
    public void deprecate() {
        if (this.isDeleted) {
            throw new IllegalDeprecationStateException(
                    "삭제된 " + resourceLabel() + " 는 deprecate 할 수 없습니다 : " + resourceId());
        }
        if (this.isDeprecated) {
            throw new IllegalDeprecationStateException(
                    "이미 deprecated 상태입니다 : " + resourceId());
        }
        this.isDeprecated = true;
        this.deprecatedAt = Instant.now();
    }

    @Override
    public void undeprecate() {
        if (!this.isDeprecated) {
            throw new IllegalDeprecationStateException(
                    "deprecated 상태가 아닙니다 : " + resourceId());
        }
        this.isDeprecated = false;
        this.deprecatedAt = null;
    }
}
