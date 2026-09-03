package com.example.serverprovision.execution.wininstall.spi;

import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.ObservationSeverity;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceCondition;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 시스템 자산 대시보드의 "Windows 설치 소스" 영역(Service Provider Interface — 도메인이 구현해 끼우는 확장점).
 * dhcpd 영역처럼 파일 봉인이 아닌 관측 영역이다 — 파일 3종의 존재, install.wim 의 이미지 목록, 전역 운영 설정의
 * 설정됨 · 미설정을 보인다(토론 1호 Q7). 비밀값(공유 비밀번호 · 제품 키)은 어떤 형태로도 화면에 내지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallSourceArea implements SystemAssetArea {

    private static final String SET = "설정됨";
    private static final String UNSET = "미설정";

    private final WindowsInstallProperties properties;
    private final WindowsImageCatalog catalog;

    @Override
    public SystemAssetAreaKey areaKey() {
        return SystemAssetAreaKey.WINDOWS_INSTALL;
    }

    @Override
    public String displayName() {
        return "Windows 설치 소스 (Samba 공유)";
    }

    @Override
    public AreaAvailability availability() {
        return properties.configured() ? AreaAvailability.CONFIGURED : AreaAvailability.NOT_CONFIGURED;
    }

    @Override
    public List<SystemAssetSlot> slots() {
        return Arrays.stream(InstallSourceSlot.values()).map(slot -> (SystemAssetSlot) slot).toList();
    }

    /**
     * install.wim 은 카탈로그 판정(해석 실패 포함)을 그대로 쓰고, 나머지 둘은 존재만 본다.
     * 미설정 · 부재 · 해석 실패를 전부 판정으로 흡수하므로 예외로 거절하지 않는다.
     */
    @Override
    public AssetSlotStatus inspect(SystemAssetSlot slot) {
        Path root = properties.sourceRootPath().orElse(null);
        if (root == null) {
            return AssetSlotStatus.notPresent(InstallSourceCondition.NOT_CONFIGURED);
        }
        InstallSourceSlot source = unwrap(slot);
        if (source == InstallSourceSlot.INSTALL_WIM) {
            InstallSourceSnapshot snapshot = catalog.snapshot();
            return snapshot.condition() == InstallSourceCondition.MISSING
                    ? AssetSlotStatus.notPresent(InstallSourceCondition.MISSING)
                    : AssetSlotStatus.present(snapshot.sizeBytes(), snapshot.modifiedAt(), snapshot.condition());
        }
        Path file = source.resolve(root);
        if (!Files.isRegularFile(file)) {
            return AssetSlotStatus.notPresent(InstallSourceCondition.MISSING);
        }
        return AssetSlotStatus.present(sizeOf(file), modifiedAtOf(file), InstallSourceCondition.PRESENT);
    }

    /** 봉인 개념이 없다 — 소스는 운영 절차가 통째로 교체한다. */
    @Override
    public boolean supportsSeal() {
        return false;
    }

    /** 영역 헤더 chip — 구성일 때만. 값이 나가는 것은 이미지 요약 · 공유 UNC · 시간대뿐이다. */
    @Override
    public List<AssetContextItem> context() {
        if (availability() != AreaAvailability.CONFIGURED) {
            return List.of();
        }
        InstallSourceSnapshot snapshot = catalog.snapshot();
        List<AssetContextItem> items = new ArrayList<>();
        items.add(snapshot.ready()
                ? new AssetContextItem("설치 이미지", snapshot.images().size() + "종 · 빌드 "
                        + snapshot.build().orElse("?") + " · " + snapshot.language().orElse("?"), ObservationSeverity.INFO)
                : new AssetContextItem("설치 이미지", "없음", ObservationSeverity.WARN));
        items.add(isSet(properties.shareUnc())
                ? new AssetContextItem("공유 UNC", properties.shareUnc().trim(), ObservationSeverity.INFO)
                : new AssetContextItem("공유 UNC", UNSET, ObservationSeverity.WARN));
        items.add(properties.shareConfigured()
                ? new AssetContextItem("공유 계정", SET, ObservationSeverity.OK)
                : new AssetContextItem("공유 계정", UNSET, ObservationSeverity.WARN));
        items.add(new AssetContextItem("시간대", properties.effectiveTimeZone(), ObservationSeverity.INFO));
        WindowsInstallProperties.ProductKeys keys = properties.productKeysOrEmpty();
        for (String editionId : snapshot.editionIds()) {
            items.add(keys.forEdition(editionId).isPresent()
                    ? new AssetContextItem("제품 키 · " + editionId, SET, ObservationSeverity.OK)
                    : new AssetContextItem("제품 키 · " + editionId, UNSET, ObservationSeverity.WARN));
        }
        return List.copyOf(items);
    }

    private static InstallSourceSlot unwrap(SystemAssetSlot slot) {
        if (slot instanceof InstallSourceSlot source) {
            return source;
        }
        throw new IllegalArgumentException("Windows 설치 소스 영역의 슬롯이 아닙니다 : " + slot.slotKey());
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Instant modifiedAtOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
