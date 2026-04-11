package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserRequest {

    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    private final String username;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private final String password;

    // null → false. root 사용자는 도메인에서 자동으로 sudo 설정됨
    private final Boolean isSudoer;

    private final boolean isPasswordEncrypted;

    @JsonCreator
    public UserRequest(
            @JsonProperty("username")            String username,
            @JsonProperty("password")            String password,
            @JsonProperty("isSudoer")            Boolean isSudoer,
            @JsonProperty("isPasswordEncrypted") boolean isPasswordEncrypted) {
        this.username            = username;
        this.password            = password;
        this.isSudoer            = isSudoer;
        this.isPasswordEncrypted = isPasswordEncrypted;
    }
}
