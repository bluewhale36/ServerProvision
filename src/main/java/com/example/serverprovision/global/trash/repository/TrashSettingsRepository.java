package com.example.serverprovision.global.trash.repository;

import com.example.serverprovision.global.trash.entity.TrashSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * S5-2-4 — trash_settings 테이블 접근.
 *
 * <p><strong>Singleton 보장</strong> : {@code count() == 1} 검사 기반 (사용자 결정 2026-05-20).
 * service 는 {@link #count()} 가:</p>
 * <ul>
 *   <li>0 — {@link TrashSettings#defaults()} 시드 insert 후 응답</li>
 *   <li>1 — {@link #findFirst()} 정상 응답</li>
 *   <li>&gt;=2 — {@link #findFirst()} 첫 row 사용 + log.warn (운영자 알림)</li>
 * </ul>
 *
 * <p>id 는 IDENTITY (AUTO_INCREMENT) — singleton 강제는 service 단의 count 검사로만.</p>
 */
public interface TrashSettingsRepository extends JpaRepository<TrashSettings, Long> {

    /**
     * 가장 오래된 row 1개. count()==1 이면 곧 유일 row, &gt;=2 이면 가장 오래된 row 가 진실의 원천.
     * id ASC 정렬 — count >=2 의 비정상 상태에서도 결정성 유지.
     */
    Optional<TrashSettings> findFirstByOrderByIdAsc();
}
