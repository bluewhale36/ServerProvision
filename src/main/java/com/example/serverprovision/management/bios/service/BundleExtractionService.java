package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.EmptyBundleException;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 번들 파일을 {@code targetDirectory} 에 전개. 상세 5-케이스 분기는 클래스 상단 Javadoc 참조 (CP2 기록).
 */
@Slf4j
@Service
public class BundleExtractionService {

    /** 폴더 업로드 — 케이스 1 거절 + 케이스 2 prefix 제거. */
    public void extractFolder(MultipartFile[] files, Path targetDirectory) {
        if (files == null || files.length == 0) {
            throw new EmptyBundleException();
        }

        // 1) 공통 최상위 prefix 검증 — 모든 파일이 동일 first-segment 로 시작해야 한다.
        Set<String> prefixes = new HashSet<>();
        for (MultipartFile f : files) {
            String rel = normalize(f.getOriginalFilename());
            if (rel.isEmpty()) {
                throw new BundleExtractionException(
                        "개별 파일 여러 개를 한 폴더로 묶지 않고 업로드할 수 없습니다 (케이스 1 거절).");
            }
            int slash = rel.indexOf('/');
            if (slash < 0) {
                // 단일 파일을 폴더 모드로 보낸 경우도 케이스 1 로 간주하고 거절
                throw new BundleExtractionException(
                        "파일에 폴더 prefix 가 없습니다. 폴더를 통째로 선택해 업로드하세요 (케이스 1 거절).");
            }
            prefixes.add(rel.substring(0, slash));
            if (prefixes.size() > 1) {
                throw new BundleExtractionException(
                        "여러 폴더가 섞여 있습니다. 단일 폴더만 선택해 업로드하세요.");
            }
        }

        ensureEmptyDir(targetDirectory);

        // 2) prefix 제거 후 저장
        for (MultipartFile f : files) {
            String rel = normalize(f.getOriginalFilename());
            int slash = rel.indexOf('/');
            String stripped = rel.substring(slash + 1);
            if (stripped.isEmpty()) continue; // 빈 디렉토리 엔트리 보호
            Path dest = safeResolve(targetDirectory, stripped);
            try {
                Files.createDirectories(dest.getParent());
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new BundleExtractionException("폴더 파일 저장 실패 : " + dest, e);
            }
        }
    }

    /** zip 업로드 — 케이스 3 그대로 · 케이스 4 prefix 제거. zip slip 방어. */
    public void extractZip(MultipartFile zipFile, Path targetDirectory) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new EmptyBundleException();
        }

        Path tempZip;
        try {
            tempZip = Files.createTempFile("bios-upload-", ".zip");
            try (InputStream in = zipFile.getInputStream()) {
                Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BundleExtractionException("zip 임시 저장 실패", e);
        }

        try (ZipFile zf = new ZipFile(tempZip.toFile())) {
            List<ZipEntry> entries = new ArrayList<>();
            zf.stream().forEach(entries::add);
            if (entries.isEmpty()) {
                throw new EmptyBundleException();
            }

            String commonPrefix = detectCommonPrefix(entries);

            ensureEmptyDir(targetDirectory);

            for (ZipEntry e : entries) {
                String normalized = e.getName().replace('\\', '/');
                if (normalized.endsWith("/")) continue; // 디렉토리 엔트리 스킵
                if (commonPrefix != null && !commonPrefix.isEmpty() && normalized.startsWith(commonPrefix)) {
                    normalized = normalized.substring(commonPrefix.length());
                }
                if (normalized.isEmpty()) continue;
                Path dest = safeResolve(targetDirectory, normalized);
                try {
                    Files.createDirectories(dest.getParent());
                    try (InputStream es = zf.getInputStream(e)) {
                        Files.copy(es, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ioe) {
                    throw new BundleExtractionException("zip 엔트리 저장 실패 : " + dest, ioe);
                }
            }
        } catch (IOException e) {
            throw new BundleExtractionException("zip 처리 실패", e);
        } finally {
            try { Files.deleteIfExists(tempZip); } catch (IOException ignored) { /* best effort */ }
        }
    }

    /** 단일 파일 업로드 (ASUS .cap 등). */
    public void extractSingleFile(MultipartFile singleFile, Path targetDirectory) {
        if (singleFile == null || singleFile.isEmpty()) {
            throw new EmptyBundleException();
        }
        String name = singleFile.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new BundleExtractionException("파일명이 누락되어 있습니다.");
        }
        // 원본 파일명만 사용 — 경로 조작 방지
        Path clean = Path.of(name).getFileName();
        if (clean == null) {
            throw new BundleExtractionException("파일명 파싱 실패 : " + name);
        }
        ensureEmptyDir(targetDirectory);
        Path dest = targetDirectory.resolve(clean);
        try {
            try (InputStream in = singleFile.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BundleExtractionException("단일 파일 저장 실패 : " + dest, e);
        }
    }

    // ---- 헬퍼 --------------------------------------------------------

    private static String normalize(String name) {
        if (name == null) return "";
        return name.replace('\\', '/').replaceFirst("^/+", "");
    }

    /** 케이스 3/4 구분. 모든 엔트리가 같은 first-segment 로 시작하고 그 first-segment 가 디렉토리 자체 엔트리로 존재하면 prefix. */
    private static String detectCommonPrefix(List<ZipEntry> entries) {
        String candidate = null;
        for (ZipEntry e : entries) {
            String name = e.getName().replace('\\', '/');
            int slash = name.indexOf('/');
            String first = slash < 0 ? name : name.substring(0, slash);
            if (candidate == null) {
                candidate = first;
            } else if (!candidate.equals(first)) {
                return null; // prefix 가 일관되지 않음 → 케이스 3
            }
        }
        if (candidate == null || candidate.isEmpty()) return null;
        // first-segment 만 있는 엔트리가 있으면 (파일 자체) 케이스 3 으로 간주
        for (ZipEntry e : entries) {
            String name = e.getName().replace('\\', '/');
            if (!name.contains("/")) {
                // 엔트리가 "파일1" 처럼 슬래시 없이 루트 파일 → 케이스 3
                return null;
            }
        }
        return candidate + "/";
    }

    private static Path safeResolve(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new BundleExtractionException(
                    "zip/path slip 탐지 — 대상 경로가 지정 디렉토리 밖에 있습니다 : " + relative);
        }
        return resolved;
    }

    private static void ensureEmptyDir(Path targetDirectory) {
        try {
            if (Files.exists(targetDirectory)) {
                if (!Files.isDirectory(targetDirectory)) {
                    throw new BundleExtractionException(
                            "대상 경로가 디렉토리가 아닙니다 : " + targetDirectory);
                }
                try (var entries = Files.list(targetDirectory)) {
                    if (entries.anyMatch(p -> !BundleFilePolicy.isIgnorable(p))) {
                        throw new BundleExtractionException(
                                "대상 디렉토리가 비어있지 않습니다 (전개 전 검증) : " + targetDirectory);
                    }
                }
            } else {
                Files.createDirectories(targetDirectory);
            }
        } catch (IOException e) {
            throw new BundleExtractionException("대상 디렉토리 준비 실패 : " + targetDirectory, e);
        }
    }
}
