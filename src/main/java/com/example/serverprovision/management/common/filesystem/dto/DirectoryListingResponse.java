package com.example.serverprovision.management.common.filesystem.dto;

import java.util.List;

/**
 * 서버 파일시스템 탐색 응답.
 */
public record DirectoryListingResponse(
        String path,
        String parentPath,
        List<Entry> entries
) {
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
