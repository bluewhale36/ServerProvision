package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.maintenance.trash.dto.response.TrashItemResponse;
import com.example.serverprovision.maintenance.trash.service.TrashTtlExtensionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.stream.Collectors;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;

/**
 * MK3 — `/maintenance/trash` 페이지 + 보존기간 연장 endpoint.
 *
 * <p>4 도메인 (ISO / BIOS / BMC / Subprogram) 의 trash 자원을 합본해 페이지 모델에 노출.
 * 도메인 분기는 {@link MarkableScanner} SPI 다형성으로 응집 — 본 컨트롤러는 도메인 모르고 합산만.</p>
 */
@Slf4j
@Controller
@RequestMapping("/maintenance/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashTtlExtensionService trashTtlExtensionService;
    private final List<MarkableScanner> scanners;
    private final TrashPolicy trashPolicy;

    private Map<ResourceType, MarkableScanner> scannersByType() {
        return scanners.stream().collect(Collectors.toMap(MarkableScanner::supportedType, s -> s));
    }

    /** TTL 7일 이내면 UI 강조. */
    private static final Duration TTL_WARNING_THRESHOLD = Duration.ofDays(7);

    /** 휴지통 페이지 렌더. */
    @GetMapping
    public String list(Model model) {
        Duration ttl = trashPolicy.getTtl();
        Instant now = Instant.now();

        List<TrashItemResponse> items = new ArrayList<>();
        for (MarkableScanner scanner : scanners) {
            // 정상 trash 자원
            for (Markable m : scanner.findTrashed()) {
                TrashItemResponse item = toResponse(m, ttl, now, false);
                if (item != null) items.add(item);
            }
            // MK3-1 — ghost row 합본. 4 영역 일관성을 위해 휴지통에 노출 + "정리" 액션만 활성.
            for (Markable m : scanner.findGhostMarkables()) {
                TrashItemResponse item = toResponse(m, ttl, now, true);
                if (item != null) items.add(item);
            }
        }
        // ghost (trashedAt=null) 가 nullsLast 로 뒤에 오게 정렬.
        items.sort(Comparator.comparing(TrashItemResponse::trashedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("trashItems", items);
        return "maintenance/trash/list";
    }

    /** 자원별 보존기간 연장 (DCN-NEW4 a). */
    @PostMapping("/{resourceType}/{resourceId}/extend")
    public String extendTtl(@PathVariable("resourceType") ResourceType resourceType,
                            @PathVariable("resourceId") Long resourceId) {
        trashTtlExtensionService.extend(resourceType, resourceId);
        return "redirect:/maintenance/trash";
    }

    /**
     * MK3 — 휴지통 페이지에서 직접 복원. 도메인-agnostic — scanner SPI 다형성으로 도메인별 service 위임.
     * 4 단계 검증 + 마커 재발급은 도메인 service 책임 (trashLifecycleService 위임).
     */
    @PostMapping("/{resourceType}/{resourceId}/restore")
    public String restore(@PathVariable("resourceType") ResourceType resourceType,
                          @PathVariable("resourceId") Long resourceId) {
        MarkableScanner scanner = scannersByType().get(resourceType);
        if (scanner == null) {
            throw new IllegalArgumentException("지원하지 않는 자원 종류 : " + resourceType);
        }
        scanner.restoreFromTrash(resourceId);
        log.info("[trash] restore type={} id={}", resourceType, resourceId);
        return "redirect:/maintenance/trash";
    }

    /**
     * MK3 — 휴지통 페이지에서 직접 영구삭제. S5-2-2 의 typed-name 검증은 후속 — 본 endpoint 는 단순 confirm 만.
     */
    @PostMapping("/{resourceType}/{resourceId}/purge")
    public String purge(@PathVariable("resourceType") ResourceType resourceType,
                        @PathVariable("resourceId") Long resourceId) {
        MarkableScanner scanner = scannersByType().get(resourceType);
        if (scanner == null) {
            throw new IllegalArgumentException("지원하지 않는 자원 종류 : " + resourceType);
        }
        scanner.purgeFromTrash(resourceId);
        log.info("[trash] purge type={} id={}", resourceType, resourceId);
        return "redirect:/maintenance/trash";
    }

    /**
     * Markable + lifecycle 메타를 TrashItemResponse 로 변환.
     * <ul>
     *   <li>{@code ghost=false} : 정상 trash 자원 — trashed_at / trashed_path 모두 있어야 함.
     *       값 없는 (자원 부재) 케이스는 ghost 패스에서 별도 합본되므로 여기선 응답 제외 (null).</li>
     *   <li>{@code ghost=true} : MK3-1 ghost row — trashed_at / trashed_path null 이 정상.
     *       UI 의 "복구 불가" 배지 + 정리 버튼만 활성화 트리거.</li>
     * </ul>
     */
    private TrashItemResponse toResponse(Markable m, Duration ttl, Instant now, boolean ghost) {
        // Markable 자체엔 trashed_at / trashed_path 가 없음 — LifecycleEntity 로 cast.
        // 단 모든 trash 적용 도메인 entity 는 LifecycleEntity 상속 + Markable 구현이므로 cast 안전.
        if (!(m instanceof com.example.serverprovision.global.entity.LifecycleEntity lifecycle)) {
            return null;
        }
        Instant trashedAt = lifecycle.getTrashedAt();
        String trashedPath = lifecycle.getTrashedPath();
        if (!ghost && (trashedAt == null || trashedPath == null)) {
            // 정상 trash 패스에서 null 메타 발견 — A2 / ghost 영역의 책임이므로 여기선 무시.
            return null;
        }
        Instant expiresAt = (trashedAt != null) ? trashedAt.plus(ttl) : null;
        boolean ttlWarning = !ghost && expiresAt != null
                && Duration.between(now, expiresAt).compareTo(TTL_WARNING_THRESHOLD) <= 0;
        return new TrashItemResponse(
                m.getResourceType(),
                m.getResourceId(),
                m.getResourceType().name() + "#" + m.getResourceId(), // displayName : 도메인 override 후속 보강
                m.getResourcePath().toString(),
                trashedPath,
                trashedAt,
                expiresAt,
                ttlWarning,
                ghost);
    }

    /**
     * MK3-1 — Ghost row 정리 (DB row hard-delete). 휴지통 페이지의 ghost 행 "정리" 버튼 진입점.
     * 도메인-agnostic — scanner SPI 의 {@code applyGhostClear} 위임. ghost 가 아닌 row 호출 시 scanner 가 거절.
     */
    @PostMapping("/{resourceType}/{resourceId}/clear-ghost")
    public String clearGhost(@PathVariable("resourceType") ResourceType resourceType,
                             @PathVariable("resourceId") Long resourceId) {
        MarkableScanner scanner = scannersByType().get(resourceType);
        if (scanner == null) {
            throw new IllegalArgumentException("지원하지 않는 자원 종류 : " + resourceType);
        }
        scanner.applyGhostClear(resourceId);
        log.info("[trash] ghost cleared. type={} id={}", resourceType, resourceId);
        return "redirect:/maintenance/trash";
    }
}
