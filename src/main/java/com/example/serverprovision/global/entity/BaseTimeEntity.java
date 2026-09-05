package com.example.serverprovision.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티의 생성/수정 시각 감사 필드.
 * {@code @EnableJpaAuditing} 이 켜져 있어야 동작한다 ({@code ServerProvisionApplication}).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	/**
	 * 자식 컬렉션만 바뀌는 수정에서 부모를 dirty 로 만든다(HF12). 단방향 컬렉션 변경은 부모 UPDATE 를 내지 않아
	 * 감사 리스너가 돌지 않고 {@code updated_at} 이 멈춘다 — 실제 값은 flush 시 리스너가 다시 덮는다.
	 */
	protected void touch() {
		this.updatedAt = LocalDateTime.now();
	}
}
