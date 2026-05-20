package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.marker.ResourceType;

/**
 * S5-2-4 — {@code (resourceType, resourceId)} 자원 식별자 묶음 record.
 *
 * <p>repository 의 JPQL constructor expression + service / controller / view 가 모두 동일 record 를
 * 공유한다. inner record 대신 top-level 로 분리 — JPQL 의 fully qualified constructor 호출에
 * Hibernate 가 안전하게 인식하도록 함.</p>
 */
public record ResourceKey(ResourceType resourceType, Long resourceId) {
}
