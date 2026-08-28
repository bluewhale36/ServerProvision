package com.example.serverprovision.management.bios;

import com.example.serverprovision.management.bios.entity.BoardBIOS;

import java.util.List;
import java.util.Optional;

/**
 * "이 보드의 최신 BIOS" 술어 하나(E3-3 Q1) — 순위(version_rank) 순 목록에서 삭제되지 않은 활성 첫 후보.
 * 자원 목록의 최신 태그({@code BiosService}) · 굽기 목표 해석({@code FirmwareResolver}) · 레지스트리 해석
 * ({@code BiosRegistryResolver})이 전부 이 메서드를 부르므로 화면이 보여주는 것과 실행이 고르는 것이 어긋나지 않는다.
 */
public final class BoardBiosCatalog {

    private BoardBiosCatalog() {
    }

    public static Optional<BoardBIOS> latestEnabled(List<BoardBIOS> rankOrdered) {
        return rankOrdered.stream()
                .filter(bios -> !bios.isDeleted() && bios.isEnabled())
                .findFirst();
    }
}
