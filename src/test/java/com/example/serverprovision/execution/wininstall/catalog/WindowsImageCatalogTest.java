package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-2 CP4 — 설치 소스 카탈로그: 판정 4종 · 이미지 채집 · (크기 · mtime) 캐시. */
class WindowsImageCatalogTest {

    @TempDir
    Path root;

    private static WindowsInstallProperties props(String sourceRoot) {
        return new WindowsInstallProperties(sourceRoot, null, null, null, null, null);
    }

    @Test
    @DisplayName("루트 미설정 → NOT_CONFIGURED · 루트만 있음 → MISSING · 깨진 파일 → UNREADABLE (모두 예외 없이 판정)")
    void snapshot_conditions() throws IOException {
        assertThat(new WindowsImageCatalog(props("")).snapshot().condition()).isEqualTo(InstallSourceCondition.NOT_CONFIGURED);
        assertThat(new WindowsImageCatalog(props(null)).snapshot().ready()).isFalse();

        WindowsImageCatalog missing = new WindowsImageCatalog(props(root.toString()));
        assertThat(missing.snapshot().condition()).isEqualTo(InstallSourceCondition.MISSING);

        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("sources/install.wim"), "not a wim file ".repeat(32));
        InstallSourceSnapshot unreadable = new WindowsImageCatalog(props(root.toString())).snapshot();
        assertThat(unreadable.condition()).isEqualTo(InstallSourceCondition.UNREADABLE);
        assertThat(unreadable.images()).isEmpty();
        assertThat(unreadable.sizeBytes()).isPositive();
    }

    @Test
    @DisplayName("가짜 WIM → PRESENT · ready · 이미지 4종 · 에디션 2종(등장 순) · 빌드 · 언어 · find 정확 일치")
    void snapshot_present() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());

        InstallSourceSnapshot snapshot = new WindowsImageCatalog(props(root.toString())).snapshot();

        assertThat(snapshot.condition()).isEqualTo(InstallSourceCondition.PRESENT);
        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.images()).hasSize(4);
        assertThat(snapshot.editionIds()).containsExactly("ServerStandard", "ServerDatacenter");
        assertThat(snapshot.build()).contains("10.0.26100.1742");
        assertThat(snapshot.language()).contains("ko-KR");
        assertThat(snapshot.find(new WindowsImageName(FakeWim.STANDARD_DESKTOP))).isPresent();
        assertThat(snapshot.find(new WindowsImageName("windows server 2025 serverstandard"))).isEmpty(); // 대소문자 구분
        assertThat(snapshot.find(null)).isEmpty();
    }

    @Test
    @DisplayName("캐시 — 크기 · mtime 이 같으면 같은 스냅샷, 소스를 교체하면 다음 호출이 다시 읽는다")
    void snapshot_cacheByMtime() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        WindowsImageCatalog catalog = new WindowsImageCatalog(props(root.toString()));

        InstallSourceSnapshot first = catalog.snapshot();
        assertThat(catalog.snapshot()).isSameAs(first);

        Path wim = root.resolve("sources/install.wim");
        Files.write(wim, FakeWim.bytes(FakeWim.twoImageXml()));
        Files.setLastModifiedTime(wim, FileTime.from(Instant.now().plusSeconds(5)));

        InstallSourceSnapshot second = catalog.snapshot();
        assertThat(second).isNotSameAs(first);
        assertThat(second.images()).hasSize(2);

        Files.delete(wim);
        assertThat(catalog.snapshot().condition()).isEqualTo(InstallSourceCondition.MISSING);
    }
}
