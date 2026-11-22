package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.Objects;

/** 설치 중 차례로 실행할 사용자 쉘 스크립트. 번호는 목록 순서(1 부터)이고 실행 기록 파일 이름에 쓴다. */
public record UserScript(String fileName, String content) {

    public UserScript {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
    }

    @Override
    public String toString() {
        return "UserScript[fileName=" + fileName + ", bytes=" + content.length() + "]";
    }
}
