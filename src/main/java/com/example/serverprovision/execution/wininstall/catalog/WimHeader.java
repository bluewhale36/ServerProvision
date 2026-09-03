package com.example.serverprovision.execution.wininstall.catalog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * WIM 파일 헤더(208 바이트)에서 이미지 수와 XML 자원의 위치만 읽는다.
 * XML 자원 헤더는 오프셋 72 의 24 바이트 — 첫 u64 의 하위 56 비트가 크기, 상위 8 비트가 플래그, 둘째 u64 가 파일 내 오프셋이다
 * (실측 1호 · Windows Server 2025 install.wim 으로 확인).
 */
public record WimHeader(int imageCount, long xmlOffset, long xmlSize) {

    public static final int SIZE = 208;

    private static final byte[] MAGIC = "MSWIM\0\0\0".getBytes(StandardCharsets.US_ASCII);
    private static final int OFFSET_CB_SIZE = 8;
    private static final int OFFSET_IMAGE_COUNT = 44;
    private static final int OFFSET_XML_RESHDR = 72;
    private static final long RESHDR_SIZE_MASK = 0x00FF_FFFF_FFFF_FFFFL;
    private static final int RESHDR_FLAG_COMPRESSED = 0x4;

    public static WimHeader parse(ByteBuffer buffer) {
        if (buffer.remaining() < SIZE) {
            throw new WimFormatException("WIM 헤더가 " + SIZE + " 바이트보다 짧습니다: " + buffer.remaining());
        }
        ByteBuffer little = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int base = little.position();
        for (int i = 0; i < MAGIC.length; i++) {
            if (little.get(base + i) != MAGIC[i]) {
                throw new WimFormatException("WIM 매직이 아닙니다.");
            }
        }
        int cbSize = little.getInt(base + OFFSET_CB_SIZE);
        if (cbSize != SIZE) {
            throw new WimFormatException("WIM 헤더 크기가 208 이 아닙니다: " + cbSize);
        }
        int imageCount = little.getInt(base + OFFSET_IMAGE_COUNT);
        long sizeAndFlags = little.getLong(base + OFFSET_XML_RESHDR);
        long xmlSize = sizeAndFlags & RESHDR_SIZE_MASK;
        int flags = (int) (sizeAndFlags >>> 56);
        long xmlOffset = little.getLong(base + OFFSET_XML_RESHDR + 8);
        if ((flags & RESHDR_FLAG_COMPRESSED) != 0) {
            throw new WimFormatException("압축된 XML 자원은 지원하지 않습니다.");
        }
        if (xmlSize <= 0 || xmlOffset < SIZE) {
            throw new WimFormatException("XML 자원 위치가 올바르지 않습니다: offset=" + xmlOffset + " size=" + xmlSize);
        }
        return new WimHeader(imageCount, xmlOffset, xmlSize);
    }
}
