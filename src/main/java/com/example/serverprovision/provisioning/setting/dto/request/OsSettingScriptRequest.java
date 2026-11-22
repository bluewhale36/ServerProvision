package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OS 후처리에서 설치 중 실행할 사용자 쉘 스크립트 하나(E4-1-b-7). 내용을 정의서 안에 그대로 담아 할당 스냅숏과 함께 동결한다.
 * Windows 에서 만든 파일이 bash 에서 {@code $'\r'} 오류를 내지 않도록 줄 끝을 LF 로 맞춰 받는다.
 *
 * @param fileName 사용자가 올린 파일 이름. 화면 표시와 실행 기록 대조에만 쓴다
 */
public record OsSettingScriptRequest(
        @NotBlank(message = "스크립트 파일 이름은 필수 값입니다.")
        @Size(max = MAX_FILE_NAME, message = "스크립트 파일 이름은 128자 이하여야 합니다.")
        String fileName,

        @NotBlank(message = "내용이 없는 파일은 올릴 수 없습니다.")
        String content
) {

    /** 스크립트 하나의 UTF-8 바이트 상한. 폼이 같은 값을 받아 업로드 즉시 거절한다. */
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_FILE_NAME = 128;

    @JsonCreator
    public OsSettingScriptRequest(@JsonProperty("fileName") String fileName,
                                  @JsonProperty("content") String content) {
        this.fileName = fileName;
        this.content = content == null ? null : content.replace("\r\n", "\n");
    }

    /** 정의서 상세가 줄 번호와 함께 보이는 원문 줄. 빈 줄도 한 줄로 세고, 파일 끝 줄바꿈은 빈 줄로 세지 않는다. */
    @JsonIgnore
    public List<String> contentLines() {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> lines = List.of(content.split("\n", -1));
        return content.endsWith("\n") ? lines.subList(0, lines.size() - 1) : lines;
    }

    @JsonIgnore
    @AssertTrue(message = "스크립트 파일은 64 KB 이하여야 합니다.")
    public boolean isWithinSizeLimit() {
        return content == null || content.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    /** NUL 이 든 파일은 텍스트가 아니다. kickstart 에는 base64 로 실리므로 그 밖의 글자는 막지 않는다. */
    @JsonIgnore
    @AssertTrue(message = "텍스트 파일만 올릴 수 있습니다.")
    public boolean isText() {
        return content == null || content.indexOf('\0') < 0;
    }
}
