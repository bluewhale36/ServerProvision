package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 설치 소스의 {@code sources/install.wim} 에서 이미지 목록을 읽는 카탈로그(토론 1호 D9 층 B 의 "자동 채집").
 * 파일이 4.8 GB 라도 헤더 208 바이트와 XML 구간만 랜덤 액세스로 읽으므로 호출 비용은 ms 단위다.
 * 캐시 키는 (크기 · 수정시각) — 운영 절차가 소스를 교체하면 다음 호출이 저절로 다시 읽는다.
 *
 * <p>ISO 자원에서 직접 읽지 않는 이유(plan D-1): 앱 계정은 loop mount 권한이 없고 bsdtar 는 UDF 를 읽지 못한다.
 * 운영 절차가 배치한 실물이 곧 setup.exe 가 읽는 것이라 여기서 읽는 편이 거짓 양성이 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsImageCatalog {

    static final Path INSTALL_WIM = Path.of("sources", "install.wim");
    /** XML 자원 상한 — 실측 11.8 KB. 헤더가 깨져 터무니없는 크기를 가리키면 읽지 않는다. */
    private static final long MAX_XML_BYTES = 16L * 1024 * 1024;

    private final WindowsInstallProperties properties;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    private record Cached(long sizeBytes, Instant modifiedAt, InstallSourceSnapshot snapshot) {
    }

    public InstallSourceSnapshot snapshot() {
        Path root = properties.sourceRootPath().orElse(null);
        if (root == null) {
            return InstallSourceSnapshot.notConfigured();
        }
        Path wim = root.resolve(INSTALL_WIM);
        if (!Files.isRegularFile(wim)) {
            cache.set(null);
            return InstallSourceSnapshot.missing();
        }
        long size;
        Instant modifiedAt;
        try {
            size = Files.size(wim);
            modifiedAt = Files.getLastModifiedTime(wim).toInstant();
        } catch (IOException e) {
            log.warn("[wininstall] install.wim 메타를 읽을 수 없다: {} — {}", wim, e.getMessage());
            return InstallSourceSnapshot.unreadable(0L, null);
        }
        Cached cached = cache.get();
        if (cached != null && cached.sizeBytes == size && cached.modifiedAt.equals(modifiedAt)) {
            return cached.snapshot;
        }
        InstallSourceSnapshot fresh = read(wim, size, modifiedAt);
        cache.set(new Cached(size, modifiedAt, fresh));
        return fresh;
    }

    private InstallSourceSnapshot read(Path wim, long size, Instant modifiedAt) {
        try (FileChannel channel = FileChannel.open(wim, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(WimHeader.SIZE);
            readFully(channel, header, 0L);
            WimHeader parsed = WimHeader.parse(header.flip());
            if (parsed.xmlSize() > MAX_XML_BYTES || parsed.xmlOffset() + parsed.xmlSize() > size) {
                throw new WimFormatException("XML 자원이 파일 범위를 벗어난다: offset=" + parsed.xmlOffset()
                        + " size=" + parsed.xmlSize() + " file=" + size);
            }
            ByteBuffer xml = ByteBuffer.allocate((int) parsed.xmlSize());
            readFully(channel, xml, parsed.xmlOffset());
            List<WindowsImage> images = WimXmlReader.read(xml.array());
            log.info("[wininstall] install.wim 이미지 {}종 채집 — {}", images.size(), wim);
            return InstallSourceSnapshot.present(images, size, modifiedAt);
        } catch (WimFormatException | IOException e) {
            log.warn("[wininstall] install.wim 을 해석할 수 없다: {} — {}", wim, e.getMessage());
            return InstallSourceSnapshot.unreadable(size, modifiedAt);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        long offset = position;
        while (target.hasRemaining()) {
            int read = channel.read(target, offset);
            if (read < 0) {
                throw new WimFormatException("파일이 예상보다 짧다: position=" + offset);
            }
            offset += read;
        }
    }
}
