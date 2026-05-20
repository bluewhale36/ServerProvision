package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.trash.dto.response.PurgeLogResponse;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * S5-2-4 — purge_log 의 조회 / 통계 SPI. INSERT 는 {@link PurgeExecutor} 가 직접 수행.
 *
 * <p>CP2 시그니처. 본체는 CP4.</p>
 */
public interface PurgeLogService {

    /**
     * 자원 단위 회고. 자원의 전체 hard-delete 시도 이력을 시간순.
     * 사용 인덱스 : {@code idx_purge_log_resource}.
     */
    List<PurgeLogResponse> findByResource(ResourceType resourceType, Long resourceId);

    /**
     * 페이지 조회 — 운영자 {@code /maintenance/trash/purge-log} 페이지.
     * 모든 필터는 null 허용 (필터 안 함).
     */
    Page<PurgeLogResponse> findPage(ResourceType filterType,
                                     PurgeOrigin filterOrigin,
                                     PurgeOutcome filterOutcome,
                                     Instant from,
                                     Instant to,
                                     Pageable pageable);

    /**
     * 자원별 누적 FAILED count — attemptNumber 계산용. PurgeExecutor 가 본 메서드를 호출.
     */
    long countFailedForResource(ResourceType resourceType, Long resourceId);

    /**
     * 자원별 마지막 outcome=FAILED 인 자원 셋 — 휴지통 list 의 "재시도 대기" 배지 lookup 1 쿼리.
     * 휴지통 controller 가 페이지 렌더 시점에 호출.
     */
    Set<ResourceKey> findResourcesWithLastOutcomeFailed();
}
