package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.asset.exception.WindowsOemAssemblyRejectedException;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code sources/$OEM$} 페이로드 조립기(E4-1-a-4 D-1 · D-2) — 활성 DRIVER 자원의 트리를 {@code $1\SPV\Drivers\<id>_<슬러그>}
 * 로 복사하고 설치 후 스크립트 둘과 매니페스트를 쓴다. {@code $OEM$} 안의 임시 디렉토리에 전부 쓴 뒤 rename 으로 바꿔 끼우므로
 * 복사 중인 반쪽 트리가 게스트에 노출되지 않고, 실패하면 옛 페이로드가 남는다. 대상 판별은 트리의 {@code *.inf} 존재다 — {@code Subprogram} 에
 * OS 구분이 없기 때문이며(CP1 후속 — 속성이 생기면 {@link #candidates} 의 술어 한 줄만 바뀐다), INF 없는 DRIVER 는
 * 조립을 막지 않고 제외 목록에 남긴다(OQ-3).
 *
 * <p>{@link #blockReason} 이 대시보드 버튼의 disabled 사유와 {@link #sync} 의 409 가드를 함께 결정한다(SSOT). 검사 대상은
 * {@code $OEM$} 디렉토리와 그 안의 기존 항목({@code $$} · {@code $1})의 쓰기 가능 — 후자는 실기 2호에서 손조립본의 소유권이
 * 스왑을 막아 500 으로 샌 지점이다(HF11-3).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsOemPayloadAssembler {

    public static final String OEM_DIR = "$OEM$";
    public static final String INF_MISSING = "INF_MISSING";
    public static final String TREE_MISSING = "TREE_MISSING";

    static final Path DRIVERS_SUBDIR = Path.of("$1", "SPV", "Drivers");
    static final Path SETUPCOMPLETE_SUBPATH = Path.of("$$", "Setup", "Scripts", "SetupComplete.cmd");
    static final Path REPORT_SUBPATH = Path.of("$1", "SPV", "spv-report.ps1");

    private static final int SLUG_MAX = 40;
    /** 자원 트리 안의 무결성 마커 파일명(ProvisionMarkerService IN_TREE) — 페이로드에서 제외. */
    static final String MARKER_FILE_NAME = ".provision.json";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final WindowsInstallProperties properties;
    private final SubprogramRepository subprogramRepository;
    private final ObjectMapper objectMapper;

    /** 조립 결과 — flash 문구의 재료. */
    public record OemAssemblyResult(int assembled, int excluded, long totalBytes) {
    }

    /** 대시보드 chip · 준비도가 읽는 현재 상태. {@code assembledAt} · {@code changes} 는 매니페스트가 있을 때만. */
    public record OemPayloadStatus(OemPayloadState state, int driverCount, int excludedCount, long totalBytes,
                                   Instant assembledAt, List<String> changes) {
    }

    /**
     * 조립을 시작할 수 없는 사유 — 비어 있으면 가능. 버튼 disabled(tooltip)와 409 가드가 같은 답을 본다.
     * 쓰기 단위는 {@code sources/$OEM$} 하나다(런북 §14-1 — 앱 계정에 이 디렉토리만 쓰기 권한을 준다). 없으면 부모가 쓰기
     * 가능할 때만 만든다(샌드박스 편의) — 운영에서는 런북이 미리 만든다.
     */
    public Optional<String> blockReason() {
        Optional<Path> root = properties.sourceRootPath();
        if (root.isEmpty()) {
            return Optional.of("설치 소스 미설정 — 환경변수 WINDOWS_INSTALL_SOURCE_ROOT");
        }
        Path sources = root.get().resolve("sources");
        if (!Files.isDirectory(sources)) {
            return Optional.of("sources 디렉토리가 없습니다 — 런북 §14-1 추출 절차");
        }
        Path oem = sources.resolve(OEM_DIR);
        if (!Files.isDirectory(oem)) {
            if (!Files.isWritable(sources)) {
                return Optional.of("sources/$OEM$ 디렉토리가 없습니다 — 런북 §14-1 ($OEM$ 쓰기 권한)");
            }
            return Optional.empty();   // sync 가 만든다
        }
        if (!Files.isWritable(oem)) {
            return Optional.of("sources/$OEM$ 에 쓰기 권한이 없습니다 — 런북 §14-1 ($OEM$ 쓰기 권한)");
        }
        // HF11-3 — 기존 항목(손조립 `$$` · `$1`)이 다른 계정 소유면 스왑의 디렉토리 이동이 거부된다(디렉토리 이동은 그 디렉토리
        // 자체의 쓰기 권한을 요구). 실기 2호에서 500 으로 샌 지점 — 여기서 잡아 409 · tooltip 으로 조치를 말한다.
        for (String name : SWAP_ENTRIES) {
            Path existing = oem.resolve(name);
            if (Files.isDirectory(existing) && !Files.isWritable(existing)) {
                return Optional.of("sources/$OEM$ 안의 기존 항목(" + name + ")에 쓰기 권한이 없습니다 — 런북 §14-1 "
                        + "(chown -R provisioning:spvadmin)");
            }
        }
        return Optional.empty();
    }

    public OemPayloadStatus status() {
        Candidates c = candidates();
        Optional<WindowsOemManifest> manifest = readManifest();
        if (manifest.isEmpty()) {
            return new OemPayloadStatus(OemPayloadState.NOT_ASSEMBLED, 0, c.excluded().size(), 0L, null, List.of());
        }
        WindowsOemManifest m = manifest.get();
        List<String> changes = m.changesAgainst(c.entries(), WindowsOemTemplates.scriptsHash());
        return new OemPayloadStatus(changes.isEmpty() ? OemPayloadState.CURRENT : OemPayloadState.STALE,
                m.entries().size(), c.excluded().size(), m.totalBytes(), parseInstant(m.assembledAt()), changes);
    }

    /**
     * 조립 — 후보를 {@code $OEM$/.tmp-<ts>} 에 전부 쓴 뒤 {@code $$} · {@code $1} · 매니페스트 셋을 rename 으로 바꿔 끼운다
     * (Windows Setup 은 {@code $OEM$} 바로 아래의 {@code $$} · {@code $1} 만 복사하므로 {@code .tmp-*} · {@code .old-*} 는 보이지 않는다).
     * 조립 전 판정(미설정 · 쓰기 불가)은 409, 복사 도중의 IO 실패는 임시 디렉토리를 치우고 {@link UncheckedIOException}(500).
     */
    public OemAssemblyResult sync() {
        blockReason().ifPresent(reason -> {
            throw WindowsOemAssemblyRejectedException.of(reason);
        });
        Path target = properties.sourceRootPath().orElseThrow().resolve("sources").resolve(OEM_DIR);
        String stamp = STAMP.format(LocalDateTime.now());
        Path tmp = target.resolve(".tmp-" + stamp);
        Candidates c = candidates();
        try {
            Files.createDirectories(target);
            sweepLeftovers(target);                              // HF11-3 — 이전 실패 · 비정상 종료가 남긴 .tmp-* · .old-*
            Files.createDirectories(tmp.resolve(DRIVERS_SUBDIR));
            List<WindowsOemManifest.Entry> entries = new ArrayList<>();
            for (Subprogram s : c.eligible()) {
                String folder = s.getId() + "_" + slug(s.getName());
                Copied copied = copyTree(s.getResourcePath(), tmp.resolve(DRIVERS_SUBDIR).resolve(folder));
                entries.add(new WindowsOemManifest.Entry(s.getId(), s.getName(), s.getVersion(), s.getManifestHash(),
                        copied.files(), copied.bytes(), folder));
            }
            writeScript(tmp.resolve(SETUPCOMPLETE_SUBPATH), WindowsOemTemplates.SETUPCOMPLETE_CMD, true);
            writeScript(tmp.resolve(REPORT_SUBPATH), WindowsOemTemplates.SPV_REPORT_PS1, false);
            WindowsOemManifest manifest = new WindowsOemManifest(WindowsOemTemplates.scriptsHash(),
                    Instant.now().toString(), entries, c.excluded());
            Files.writeString(tmp.resolve(WindowsOemManifest.FILE_NAME), objectMapper.writeValueAsString(manifest),
                    StandardCharsets.UTF_8);
            swap(target, tmp, stamp);
            long total = manifest.totalBytes();
            log.info("[oem] $OEM$ 조립 완료 : 드라이버 {}종 · 제외 {} · {} bytes → {}", entries.size(), c.excluded().size(), total, target);
            return new OemAssemblyResult(entries.size(), c.excluded().size(), total);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException("$OEM$ 조립 실패 — " + e.getMessage(), e);
        }
    }

    // ── 후보 ────────────────────────────────────────────────────────────────

    record Candidates(List<Subprogram> eligible, List<WindowsOemManifest.Entry> entries,
                      List<WindowsOemManifest.Excluded> excluded) {
    }

    /** 대상 = DRIVER · 미삭제 · effective 활성 · 트리에 INF 보유(재귀). 비활성은 대상이 아니고, 트리 부재 · INF 부재는 제외 목록. */
    Candidates candidates() {
        List<Subprogram> eligible = new ArrayList<>();
        List<WindowsOemManifest.Entry> entries = new ArrayList<>();
        List<WindowsOemManifest.Excluded> excluded = new ArrayList<>();
        for (Subprogram s : subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)) {
            if (!s.isEnabled()) {
                continue;
            }
            Path tree = s.getResourcePath();
            if (!Files.isDirectory(tree)) {
                excluded.add(new WindowsOemManifest.Excluded(s.getId(), s.getName(), TREE_MISSING));
            } else if (!hasInf(tree)) {
                excluded.add(new WindowsOemManifest.Excluded(s.getId(), s.getName(), INF_MISSING));
            } else {
                eligible.add(s);
                entries.add(new WindowsOemManifest.Entry(s.getId(), s.getName(), s.getVersion(), s.getManifestHash(),
                        s.getFileCount(), s.getTotalBytes(), s.getId() + "_" + slug(s.getName())));
            }
        }
        return new Candidates(eligible, entries, excluded);
    }

    static boolean hasInf(Path tree) {
        try (Stream<Path> walk = Files.walk(tree)) {
            return walk.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".inf"));
        } catch (IOException e) {
            return false;
        }
    }

    /** 폴더명의 ASCII 슬러그 — 한글 · 공백 · 기호는 하이픈으로, 40자 상한. 비면 "driver". */
    static String slug(String name) {
        if (name == null) {
            return "driver";
        }
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > SLUG_MAX) {
            s = s.substring(0, SLUG_MAX).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "driver" : s;
    }

    // ── 파일 ────────────────────────────────────────────────────────────────

    Optional<WindowsOemManifest> readManifest() {
        Optional<Path> root = properties.sourceRootPath();
        if (root.isEmpty()) {
            return Optional.empty();
        }
        Path file = root.get().resolve("sources").resolve(OEM_DIR).resolve(WindowsOemManifest.FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), WindowsOemManifest.class));
        } catch (IOException | RuntimeException e) {
            log.warn("[oem] 매니페스트 판독 실패 — 미조립으로 본다 : {} ({})", file, e.getMessage());
            return Optional.empty();
        }
    }

    /** 복사한 파일 수 · 바이트 — 매니페스트의 fileCount · bytes 는 이 한 기준에서 온다(CP5 F-1). */
    record Copied(int files, long bytes) {
    }

    /**
     * 트리 복사 — 자원의 무결성 마커({@code .provision.json})는 프로비저닝 서버 내부 메타데이터(HMAC 서명)라 게스트로
     * 내보내지 않는다(CP5 F-1). pnputil 은 {@code *.inf} 만 보므로 설치에는 무관하지만, 표시 수치와 게스트 디스크를 깨끗이 한다.
     */
    private static Copied copyTree(Path from, Path to) throws IOException {
        int[] files = {0};
        long[] bytes = {0L};
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isMarker(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                files[0]++;
                bytes[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return new Copied(files[0], bytes[0]);
    }

    static boolean isMarker(Path file) {
        return MARKER_FILE_NAME.equals(file.getFileName().toString());
    }

    /** cmd 는 CRLF 로 쓴다(LF 만 있는 배치는 label 탐색이 어긋날 수 있다) · PowerShell 은 LF 로 충분하다. */
    private static void writeScript(Path file, String text, boolean crlf) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, crlf ? text.replace("\n", "\r\n") : text, StandardCharsets.US_ASCII);
    }

    /** {@code $OEM$} 바로 아래의 항목 셋 — 이 순서로 바꿔 끼운다. */
    private static final List<String> SWAP_ENTRIES = List.of("$$", "$1", WindowsOemManifest.FILE_NAME);

    /**
     * 항목 셋을 하나씩 rename — 옛 것은 {@code .old-<ts>/} 로 비켜 두고 새 것을 그 자리에 둔다. 중간에 실패하면 옮긴 만큼 되돌린다.
     * 항목 하나의 rename 은 원자적이고, 셋 사이의 창은 밀리초라 Setup 이 그 순간을 읽을 가능성은 무시한다.
     */
    private static void swap(Path target, Path tmp, String stamp) throws IOException {
        Path old = target.resolve(".old-" + stamp);
        Files.createDirectories(old);
        List<String> moved = new ArrayList<>();
        try {
            for (String name : SWAP_ENTRIES) {
                Path current = target.resolve(name);
                if (Files.exists(current)) {
                    Files.move(current, old.resolve(name));
                }
                Files.move(tmp.resolve(name), current);
                moved.add(name);
            }
        } catch (IOException e) {
            for (String name : moved) {                       // 되돌리기 — 새 것을 tmp 로, 옛 것을 제자리로
                Path current = target.resolve(name);
                try {
                    Files.move(current, tmp.resolve(name));
                    if (Files.exists(old.resolve(name))) {
                        Files.move(old.resolve(name), current);
                    }
                } catch (IOException rollback) {
                    log.error("[oem] 스왑 되돌리기 실패 : {} ({})", current, rollback.getMessage());
                }
            }
            deleteQuietly(old);                               // HF11-3 — 실패가 빈 .old-<ts> 를 남기지 않게
            throw e;
        }
        deleteQuietly(old);
        deleteQuietly(tmp);
    }

    /** {@code $OEM$} 바로 아래의 {@code .tmp-*} · {@code .old-*} 잔존물 제거(HF11-3) — 조립 시작 시 1회. Setup 은 이 항목을 보지 않는다. */
    static void sweepLeftovers(Path target) throws IOException {
        try (Stream<Path> list = Files.list(target)) {
            list.filter(Files::isDirectory)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(".tmp-") || n.startsWith(".old-");
                    })
                    .forEach(WindowsOemPayloadAssembler::deleteQuietly);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 정리 실패는 다음 조립의 임시 디렉토리 이름이 다르므로 치명적이지 않다 — 로그만 남긴다.
                    log.warn("[oem] 임시 디렉토리 정리 실패 : {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("[oem] 임시 디렉토리 순회 실패 : {} ({})", dir, e.getMessage());
        }
    }

    private static Instant parseInstant(String text) {
        try {
            return text == null ? null : Instant.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
