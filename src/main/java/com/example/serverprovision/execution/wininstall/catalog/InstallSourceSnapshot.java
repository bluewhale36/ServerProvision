package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 어느 한 시점의 install.wim 관측 결과 — 판정과 이미지 목록. 정의서 폼(선택지) · 검사기(가드) · 대시보드(표시)가
 * 같은 스냅샷을 읽어 한 판정을 공유한다.
 */
public record InstallSourceSnapshot(
        InstallSourceCondition condition,
        List<WindowsImage> images,
        long sizeBytes,
        Instant modifiedAt
) {

    public static InstallSourceSnapshot notConfigured() {
        return new InstallSourceSnapshot(InstallSourceCondition.NOT_CONFIGURED, List.of(), 0L, null);
    }

    public static InstallSourceSnapshot missing() {
        return new InstallSourceSnapshot(InstallSourceCondition.MISSING, List.of(), 0L, null);
    }

    public static InstallSourceSnapshot unreadable(long sizeBytes, Instant modifiedAt) {
        return new InstallSourceSnapshot(InstallSourceCondition.UNREADABLE, List.of(), sizeBytes, modifiedAt);
    }

    public static InstallSourceSnapshot present(List<WindowsImage> images, long sizeBytes, Instant modifiedAt) {
        return new InstallSourceSnapshot(InstallSourceCondition.PRESENT, List.copyOf(images), sizeBytes, modifiedAt);
    }

    /** 정의서가 이미지를 고를 수 있는 상태 — 존재하고 해석됐으며 이미지가 하나 이상이다. */
    public boolean ready() {
        return condition == InstallSourceCondition.PRESENT && !images.isEmpty();
    }

    public Optional<WindowsImage> find(WindowsImageName name) {
        if (name == null) {
            return Optional.empty();
        }
        return images.stream().filter(image -> image.name().equals(name)).findFirst();
    }

    /** 소스에 실제로 있는 에디션 식별자(등장 순서) — 제품 키 표시는 이 집합만 대상으로 한다. */
    public Set<String> editionIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (WindowsImage image : images) {
            if (!image.editionId().isBlank()) {
                ids.add(image.editionId());
            }
        }
        return ids;
    }

    public Optional<String> build() {
        return images.stream().map(WindowsImage::build).filter(b -> !b.isBlank()).findFirst();
    }

    public Optional<String> language() {
        return images.stream().map(WindowsImage::language).filter(l -> !l.isBlank()).findFirst();
    }
}
