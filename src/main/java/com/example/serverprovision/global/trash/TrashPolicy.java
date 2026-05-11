package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.marker.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * MK3 — Trash 디렉토리의 위치 / 내부 구조 / 파일명 형식 정책.
 *
 * <p>구성 :
 * <ul>
 *   <li>{@code trash.root} (DCN1=a) — `<provision-base>/.trash/` default</li>
 *   <li>도메인별 sub-디렉토리 (DCN2=b) — {@code <root>/<resourceType>/<resourceId>/}</li>
 *   <li>파일명 (DCN3=c) — {@code <originalName>_<yyMMdd-HHmmss-SSS>_<UUID8>.<ext>}</li>
 *   <li>TTL (DCN4=b) — 30일 default</li>
 *   <li>전역 OFF (DCN-NEW11) — {@code reconciliation.auto-apply} 는 PathReconciliationService 측 책임</li>
 * </ul>
 *
 * <p>본 클래스는 정책 메서드 시그니처 + Spring 프로퍼티 바인딩만 제공한다 (CP2 범위).
 * 본체 알고리즘 (timestamp+UUID8 suffix 합성, mv 보상 retry 등) 은 CP4 에서 작성.</p>
 */
@Component
@RequiredArgsConstructor
public class TrashPolicy {

    @Value("${trash.root:/opt/provisioning/.soft-deleted}")
    private String trashRoot;

    @Value("${trash.ttl.days:30}")
    private int ttlDays;

    /**
     * Trash root 절대 경로. 부재 시 {@code TrashService.moveToTrash} 가 자동 생성.
     */
    public Path getTrashRoot() {
        return Path.of(trashRoot);
    }

    /**
     * 도메인 + 자원 ID 별 trash sub-디렉토리. 예: {@code <root>/OS_ISO/25/}.
     */
    public Path resolveTrashDirectory(ResourceType resourceType, Long resourceId) {
        return getTrashRoot().resolve(resourceType.name()).resolve(String.valueOf(resourceId));
    }

    /**
     * 보존 기간 (TTL). default 30일.
     */
    public Duration getTtl() {
        return Duration.ofDays(ttlDays);
    }
}
