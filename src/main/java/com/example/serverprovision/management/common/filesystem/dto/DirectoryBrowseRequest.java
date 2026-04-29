package com.example.serverprovision.management.common.filesystem.dto;

/**
 * 서버 디렉토리 탐색 요청.
 */
public record DirectoryBrowseRequest(
        String path,
        boolean includeFiles
) {
}
