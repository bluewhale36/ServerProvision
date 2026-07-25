package com.example.serverprovision.global.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 단일 파일의 SHA-256 hex 계산. E1-I-2-b-1 에서 진단 자산 무결성({@code DiagnosticAssetIntegrityService})과
 * 버전 저장소({@code AssetHistoryService})가 공유하도록 승격한 공용 유틸(중복 금지 원칙, plan §8 Q6).
 *
 * <p>알고리즘은 승격 전 private 구현과 <b>동일</b>하다(SHA-256 · 8KB 스트리밍 · hex lower) — 기존 마커가
 * 기록한 해시와 재계산 해시가 계속 일치해야 하므로 바꾸지 않는다.</p>
 */
public final class FileDigest {

    private FileDigest() {
    }

    /**
     * @throws IOException 파일 읽기 실패(호출부가 도메인 예외로 감싼다)
     */
    public static String sha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 — 표준 JRE 라면 도달 불가", e);
        }
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, md)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) >= 0) {
                // drain — DigestInputStream 이 읽는 바이트를 해시에 누적
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
