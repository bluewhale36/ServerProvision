package com.example.serverprovision.execution.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;

/**
 * 설치 매체 파일 하나를 응답으로 조립한다. 형식은 파일 이름으로 추정하고 모르면 octet-stream 이다.
 * 큰 파일도 {@link FileSystemResource} 로 흘려보내며 {@code Range} 요청은 Spring 이 206 으로 처리한다.
 */
final class ServedFileResponse {

    private ServedFileResponse() {
    }

    static ResponseEntity<Resource> of(Path file) {
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(resource);
    }
}
