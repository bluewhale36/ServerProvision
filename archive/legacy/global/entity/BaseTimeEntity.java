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
 * JPA Auditing 기반 생성/수정 시각 자동 관리 슈퍼클래스이다.
 *
 * <p>역할: {@code @MappedSuperclass}로 선언되어 이 클래스를 상속하는 모든 엔티티
 * ({@code ServerNode}, {@code BoardModel}, {@code OSMetadata} 등)에 {@code created_at},
 * {@code updated_at} 컬럼을 투명하게 제공한다. {@code @EnableJpaAuditing}이 활성화된
 * 컨텍스트에서만 동작하며, {@code AuditingEntityListener}가 영속화·수정 이벤트를 감지해
 * 자동으로 값을 채운다.</p>
 *
 * <p>유스케이스: 프로비저닝 파이프라인 전반에서 엔티티 생성/변경 시각 추적에 사용된다.
 * 예를 들어 {@code OSMetadataService#getGroupedOSMetadata}는 {@code createdAt}을 기준으로
 * 그룹 내 정렬을 수행하여 최근 등록 순으로 목록을 반환한다. {@code createdAt}은
 * {@code @Column(updatable = false)}로 선언되어 최초 저장 이후 변경되지 않는다.</p>
 *
 * <p>확장 가이드: 새 엔티티를 추가할 때 감사 필드가 필요하면 이 클래스를 상속한다.
 * 생성 사용자·수정 사용자 추적이 필요해지면 {@code @CreatedBy}, {@code @LastModifiedBy}를
 * 이 클래스에 추가하고, Spring Security의 {@code AuditorAware} 빈을 별도 구현한다.
 * 필드를 추가할 경우 해당 필드를 참조하는 정렬·필터 로직(예: {@code OSMetadataService})도
 * 함께 검토한다.</p>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseTimeEntity {

    /** 엔티티 최초 저장 시각. JPA Auditing이 자동으로 채우며 이후 갱신되지 않는다. */
    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    /** 엔티티 마지막 수정 시각. JPA Auditing이 변경 감지 시마다 자동으로 갱신한다. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
