package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.MaliciousContentSuspectedException;
import com.example.serverprovision.global.security.exception.SuspiciousFilenameException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 업로드 콘텐츠 안전성 검증 (S3 v2 § 2.5). Apache Tika 기반 magic byte / MIME.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGuard {

    private static final byte[] PK_LOCAL = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] PK_EMPTY = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] PK_SPANNED = {0x50, 0x4B, 0x07, 0x08};

    /** Tika 가 검출하는 실행 가능 binary MIME (Linux ELF / Windows PE / macOS Mach-O 등). */
    private static final Set<String> EXECUTABLE_MIMES = Set.of(
            "application/x-executable",
            "application/x-msdownload",
            "application/x-mach-binary",
            "application/x-sharedlib",
            "application/x-dosexec",
            "application/x-elf",
            "application/vnd.microsoft.portable-executable"
    );

    /** 위험 파일명 패턴 (확장자 기준). */
    private static final List<String> SUSPICIOUS_EXTENSIONS = List.of(".lnk", ".scf");

    private final UploadSecurityProperties uploadSecurityProperties;
    private final Tika tika = new Tika();

    /**
     * ZIP 의 첫 4 byte (PK\x03\x04 / PK\x05\x06 / PK\x07\x08) magic 만 검사하는 1차 가드.
     *
     * <p><b>한계 (의도된 trade-off)</b> — magic byte 4 byte 뒤에 ELF / PE 등 임의 페이로드를 붙인 chimera 파일은
     * 본 검사를 통과한다. 본 메서드는 "최소 비용 1차 차단" 으로, 실제 zip 구조 검증은 후속 단계인
     * {@link ZipBombGuard#assertSafeZip(MultipartFile)} (central directory parse — IOException 시 거절) 와
     * {@code BundleExtractionService.extractZip} (개별 entry 풀이 시 IOException) 에서 이루어진다.
     * 따라서 chimera 파일도 zip 처리 단계 진입 전에 거절된다.</p>
     */
    public void assertSafeZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new MaliciousContentSuspectedException("ZIP 파일이 비어있습니다.");
        }
        byte[] head = readHead(zipFile, 4);
        if (head.length < 4) {
            throw new MaliciousContentSuspectedException("ZIP 파일 길이가 너무 짧습니다.");
        }
        if (!startsWith(head, PK_LOCAL) && !startsWith(head, PK_EMPTY) && !startsWith(head, PK_SPANNED)) {
            throw new MaliciousContentSuspectedException(
                    "ZIP magic byte 불일치 — 실제 콘텐츠가 zip 이 아닙니다 : " + zipFile.getOriginalFilename());
        }
    }

    /**
     * C5 — 이미 디스크에 떨어진 zip 파일에 대한 magic byte 검사 overload.
     *
     * <p>{@code MultipartFile.getInputStream()} 을 다중 소비하는 비용을 피하려고 도입. {@code BundleExtractionService}
     * 가 multipart 를 임시 파일로 옮긴 뒤 본 overload 를 호출하여 head 4 byte 만 재읽기.</p>
     */
    public void assertSafeZip(java.nio.file.Path zipPath) {
        if (zipPath == null || !java.nio.file.Files.exists(zipPath)) {
            throw new MaliciousContentSuspectedException("ZIP 파일이 비어있습니다.");
        }
        byte[] head;
        try (InputStream in = java.nio.file.Files.newInputStream(zipPath)) {
            head = in.readNBytes(4);
        } catch (IOException e) {
            throw new MaliciousContentSuspectedException("ZIP 파일 읽기 실패 : " + e.getMessage());
        }
        if (head.length < 4) {
            throw new MaliciousContentSuspectedException("ZIP 파일 길이가 너무 짧습니다.");
        }
        if (!startsWith(head, PK_LOCAL) && !startsWith(head, PK_EMPTY) && !startsWith(head, PK_SPANNED)) {
            throw new MaliciousContentSuspectedException("ZIP magic byte 불일치 — 실제 콘텐츠가 zip 이 아닙니다.");
        }
    }

    public void classifyAndApplyExecutablePolicy(MultipartFile file, ResultCollector collector) {
        if (file == null || file.isEmpty()) return;
        ExecutableBinaryPolicy policy = uploadSecurityProperties.executableBinaryPolicy();
        if (policy == ExecutableBinaryPolicy.ALLOW) return;
        String mime = detectMime(file);
        if (mime == null || !EXECUTABLE_MIMES.contains(mime)) return;
        String name = file.getOriginalFilename();
        switch (policy) {
            case DENY -> throw new ExecutableContentRejectedException(); // S3.1 (B4) — 메시지 일반화
            case WARN -> {
                // S3.1 (B5) — 사용자 입력 sanitize 후 log.
                log.warn("[content] 실행 가능 binary 감지 (WARN). file={}, mime={}",
                        sanitizeForLog(name), sanitizeForLog(mime));
                if (collector != null) {
                    collector.addWarning("실행 가능 binary 가 감지되었습니다.");
                }
            }
            default -> { /* ALLOW — already returned */ }
        }
    }

    public void classifyAndApplyExecutablePolicyForFolder(MultipartFile[] files, ResultCollector collector) {
        if (files == null || files.length == 0) return;
        if (uploadSecurityProperties.executableBinaryPolicy() == ExecutableBinaryPolicy.ALLOW) return;
        int configuredSample = uploadSecurityProperties.executableScanSampleSize();
        // C4 — sample size 가 0 이하면 전수 검사 (운영자가 "샘플링 없이 전체 검사" 를 선택한 의미).
        boolean scanAll = configuredSample <= 0;
        int sample = scanAll ? files.length : Math.min(files.length, configuredSample);
        // C4 — sample 이 전수 검사가 아니면 인덱스를 random 으로 섞어 우회 난이도 ↑ (51 번째 ELF 시도 차단).
        // 원본 배열은 변경하지 않는다 (호출자에서 다시 사용).
        int[] order = new int[files.length];
        for (int i = 0; i < files.length; i++) order[i] = i;
        if (!scanAll && sample < files.length) {
            java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
            // Fisher-Yates 부분 셔플 — 처음 sample 개를 무작위로 추출.
            for (int i = 0; i < sample; i++) {
                int j = i + rnd.nextInt(files.length - i);
                int tmp = order[i]; order[i] = order[j]; order[j] = tmp;
            }
        }
        for (int i = 0; i < sample; i++) {
            classifyAndApplyExecutablePolicy(files[order[i]], collector);
        }
    }

    public void assertNoSuspiciousFilenames(MultipartFile[] files) {
        if (files == null) return;
        SuspiciousFilenamesPolicy policy = uploadSecurityProperties.suspiciousFilenamesPolicy();
        if (policy == SuspiciousFilenamesPolicy.DISABLED) return;
        for (MultipartFile f : files) {
            if (f == null) continue;
            String name = f.getOriginalFilename();
            if (name == null) continue;
            String lower = name.toLowerCase();
            for (String ext : SUSPICIOUS_EXTENSIONS) {
                if (lower.endsWith(ext)) {
                    if (policy == SuspiciousFilenamesPolicy.DENY) {
                        throw new SuspiciousFilenameException();
                    } else {
                        log.warn("[content] suspicious filename (WARN) : {}", sanitizeForLog(name));
                    }
                }
            }
        }
    }

    /**
     * S3.1 (A4) + S3.2 (K5 / K6) — 모든 업로드 파일 / zip entry 의 이름이 안전한지 검증.
     * <p>검증 항목 :</p>
     * <ul>
     *   <li>K5 — UTF-8 byte 단위 길이 &gt; 255 byte 거절 (Linux ext4 한도)</li>
     *   <li>ASCII control char (0x00 ~ 0x1F, tab=0x09 제외) / DEL (0x7F) / C1 control (0x80~0x9F) 거절</li>
     *   <li>K6 — Unicode FORMAT 카테고리 (RLO U+202E / LRM U+200E / BOM U+FEFF / Zero-Width Space U+200B 등) 거절</li>
     *   <li>K6 — Unicode SURROGATE 카테고리 (단독 surrogate) 거절</li>
     *   <li>K6 — Unicode LINE_SEPARATOR / PARAGRAPH_SEPARATOR 거절 (NEL U+0085, U+2028, U+2029)</li>
     * </ul>
     */
    public String sanitizeFilenameOrThrow(String name) {
        if (name == null || name.isEmpty()) {
            throw new MaliciousContentSuspectedException("파일명이 비어있습니다.");
        }
        // K5 — UTF-8 byte 단위 검증 (Java char.length 가 아니라 실제 디스크 저장 byte 기준)
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255) {
            throw new MaliciousContentSuspectedException("파일명이 255 byte 를 초과했습니다 (UTF-8 기준).");
        }
        // K6 — char by char 검사. surrogate pair 도 고려.
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // ASCII control + DEL + C1 control
            if (c == '\0' || (c < 0x20 && c != '\t') || c == 0x7F || (c >= 0x80 && c < 0xA0)) {
                throw new MaliciousContentSuspectedException(
                        "파일명에 제어 문자 또는 null byte 가 포함되었습니다.");
            }
            int type = Character.getType(c);
            // K6 — Unicode 위험 카테고리
            if (type == Character.FORMAT
                    || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new MaliciousContentSuspectedException(
                        "파일명에 Unicode 위험 코드포인트 (RLO / LRM / BOM / Zero-Width / Surrogate / Line Separator 등) 가 포함되었습니다.");
            }
        }
        return name;
    }

    /**
     * S3.1 (B5) + S3.2 (K7) — 로그에 사용자 입력을 그대로 출력할 때 CRLF 인젝션 / ANSI escape / Unicode 위조를 막는 sanitize.
     * <p>치환 대상 :</p>
     * <ul>
     *   <li>ASCII control char (0x00 ~ 0x1F, tab=0x09 포함 모두) / DEL (0x7F) / C1 control (0x80~0x9F)</li>
     *   <li>Unicode FORMAT (RLO / LRM / BOM 등) / SURROGATE / LINE_SEPARATOR / PARAGRAPH_SEPARATOR</li>
     * </ul>
     * <p>ANSI escape sequence (`[31m`) 의 첫 byte (ESC=0x1B) 가 control char 라 자동 차단된다.</p>
     */
    public static String sanitizeForLog(String s) {
        if (s == null) return "(null)";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c < 0xA0)) {
                out.append('_');
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.FORMAT
                    || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                out.append('_');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String detectMime(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("[content] MIME 검출 실패 : {} ({})", file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    private static byte[] readHead(MultipartFile file, int n) {
        try (InputStream is = file.getInputStream()) {
            return is.readNBytes(n);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static boolean startsWith(byte[] buf, byte[] sig) {
        if (buf.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (buf[i] != sig[i]) return false;
        }
        return true;
    }

    public interface ResultCollector {
        void addWarning(String message);

        static ResultCollector into(List<String> sink) {
            return sink::add;
        }
    }
}
