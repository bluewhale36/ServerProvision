package com.example.serverprovision.management.common.filesystem.dto;

import java.util.List;

/**
 * 서버 파일시스템 탐색 응답.
 * <p>{@link #truncated} 가 {@code true} 이면 디렉토리에 {@code provision.browse.max-entries} 보다 많은 항목이 있어
 * 처음 N 개만 잘려 들어왔음을 의미한다 (S3 § 2.4).</p>
 */
public record DirectoryListingResponse(
        String path,
        String parentPath,
        List<Entry> entries,
        boolean truncated
) {

    /** S3 이전의 호출 호환을 위한 편의 생성자 — {@code truncated=false}. */
    public DirectoryListingResponse(String path, String parentPath, List<Entry> entries) {
        this(path, parentPath, entries, false);
    }

    public record Entry(
            String type,
            String name,
            Long size
    ) {
        public static Entry directory(String name) {
            return new Entry("DIR", name, null);
        }

        public static Entry file(String name, long size) {
            return new Entry("FILE", name, size);
        }
    }
}
