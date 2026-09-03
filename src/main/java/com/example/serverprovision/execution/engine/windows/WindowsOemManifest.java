package com.example.serverprovision.execution.engine.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code sources/$OEM$/spv-oem-manifest.json} — 조립 시점에 어떤 자원(id · manifestHash)과 어떤 스크립트(hash)를
 * 넣었는지의 기록(E4-1-a-4 D-1). 대시보드 chip 과 준비도는 이 파일을 현재 등록 상태와 대조해 최신 · 갱신 필요를 판정한다.
 * {@code $OEM$} 루트에 두므로 Windows Setup 이 게스트로 복사하지 않는다({@code $$} · {@code $1} 아래만 복사된다).
 */
public record WindowsOemManifest(
        String scriptsHash,
        String assembledAt,
        List<Entry> entries,
        List<Excluded> excluded
) {

    public static final String FILE_NAME = "spv-oem-manifest.json";

    /** 페이로드에 들어간 드라이버 자원 하나 — 폴더명은 {@code <id>_<슬러그>}. */
    public record Entry(long id, String name, String version, String manifestHash, int fileCount, long bytes, String folder) {
    }

    /** 조립 대상이었으나 뺀 자원 — 사유는 {@link WindowsOemPayloadAssembler#INF_MISSING} · {@link WindowsOemPayloadAssembler#TREE_MISSING}. */
    public record Excluded(long id, String name, String reason) {
    }

    public WindowsOemManifest {
        entries = entries == null ? List.of() : List.copyOf(entries);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
    }

    public long totalBytes() {
        return entries.stream().mapToLong(Entry::bytes).sum();
    }

    /** 지금의 조립 후보(id · manifestHash)와 내장 스크립트 해시가 이 기록과 같은가. */
    public OemPayloadState compare(List<Entry> current, String currentScriptsHash) {
        return changesAgainst(current, currentScriptsHash).isEmpty() ? OemPayloadState.CURRENT : OemPayloadState.STALE;
    }

    /** 무엇이 달라졌는지 사람이 읽을 목록 — 추가 · 제거 · 교체된 자원 이름과 스크립트. 비어 있으면 최신. */
    public List<String> changesAgainst(List<Entry> current, String currentScriptsHash) {
        List<String> changes = new ArrayList<>();
        Map<Long, Entry> recorded = entries.stream().collect(Collectors.toMap(Entry::id, Function.identity(), (a, b) -> a));
        Map<Long, Entry> now = current.stream().collect(Collectors.toMap(Entry::id, Function.identity(), (a, b) -> a));
        for (Entry e : current) {
            Entry was = recorded.get(e.id());
            if (was == null) {
                changes.add(e.name() + " 추가");
            } else if (!was.manifestHash().equals(e.manifestHash())) {
                changes.add(e.name() + " 교체");
            }
        }
        for (Entry was : entries) {
            if (!now.containsKey(was.id())) {
                changes.add(was.name() + " 제거");
            }
        }
        if (scriptsHash == null || !scriptsHash.equals(currentScriptsHash)) {
            changes.add("설치 후 스크립트 변경");
        }
        return changes;
    }
}
