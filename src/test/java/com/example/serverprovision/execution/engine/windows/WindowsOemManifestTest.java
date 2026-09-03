package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E4-1-a-4 CP4 — 매니페스트 대조: 조립 시점의 (id · manifestHash) 집합과 스크립트 해시가 지금과 같으면 최신, 하나라도
 * 다르면 갱신 필요 + 사람이 읽을 변경 목록. chip 이 이 목록을 그대로 보인다.
 */
class WindowsOemManifestTest {

    private static final String SCRIPTS = "abc123";

    private static WindowsOemManifest.Entry entry(long id, String name, String hash) {
        return new WindowsOemManifest.Entry(id, name, "1.0", hash, 3, 1024L, id + "_" + name);
    }

    private static WindowsOemManifest recorded() {
        return new WindowsOemManifest(SCRIPTS, "2026-09-03T05:00:00Z",
                List.of(entry(1, "chipset", "h1"), entry(2, "qat", "h2")),
                List.of(new WindowsOemManifest.Excluded(3, "lan-tool", WindowsOemPayloadAssembler.INF_MISSING)));
    }

    @Test
    @DisplayName("같은 집합 · 같은 스크립트 해시 → CURRENT · 변경 목록 없음 · totalBytes 합산")
    void same_current() {
        List<WindowsOemManifest.Entry> now = List.of(entry(2, "qat", "h2"), entry(1, "chipset", "h1"));   // 순서 무관

        assertThat(recorded().compare(now, SCRIPTS)).isEqualTo(OemPayloadState.CURRENT);
        assertThat(recorded().changesAgainst(now, SCRIPTS)).isEmpty();
        assertThat(recorded().totalBytes()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("자원 추가 · 교체(해시 변경) · 제거 → STALE · 변경 목록에 이름과 종류")
    void addedReplacedRemoved_stale() {
        List<WindowsOemManifest.Entry> now = List.of(entry(1, "chipset", "h1-v2"), entry(4, "nvme", "h4"));

        assertThat(recorded().compare(now, SCRIPTS)).isEqualTo(OemPayloadState.STALE);
        assertThat(recorded().changesAgainst(now, SCRIPTS)).containsExactly("chipset 교체", "nvme 추가", "qat 제거");
    }

    @Test
    @DisplayName("스크립트 원문이 바뀌면(앱 새 판) 자원이 같아도 STALE — '설치 후 스크립트 변경'")
    void scriptsChanged_stale() {
        List<WindowsOemManifest.Entry> now = List.of(entry(1, "chipset", "h1"), entry(2, "qat", "h2"));

        assertThat(recorded().compare(now, "other")).isEqualTo(OemPayloadState.STALE);
        assertThat(recorded().changesAgainst(now, "other")).containsExactly("설치 후 스크립트 변경");
    }

    @Test
    @DisplayName("null 목록은 빈 목록으로 정규화 — 손상된 매니페스트가 NPE 로 새지 않는다")
    void nullLists_normalized() {
        WindowsOemManifest m = new WindowsOemManifest(null, null, null, null);

        assertThat(m.entries()).isEmpty();
        assertThat(m.excluded()).isEmpty();
        assertThat(m.compare(List.of(), SCRIPTS)).isEqualTo(OemPayloadState.STALE);   // 스크립트 해시 null ≠ 현재
    }
}
