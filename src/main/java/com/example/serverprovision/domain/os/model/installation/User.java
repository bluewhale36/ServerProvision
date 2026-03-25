package com.example.serverprovision.domain.os.model.installation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
public class User {

    @NotNull(message = "사용자 이름은 필수 값입니다.")
    @NotBlank(message = "사용자 이름은 필수 입력값입니다.")
    private final String username;

    @NotNull(message = "비밀번호는 필수 값입니다.")
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private final String password;

    private final boolean isSudoer;
    private final boolean isRoot;
    private final boolean isPasswordEncrypted;

    @Builder
    private User(String username, String password, Boolean isSudoer, boolean isPasswordEncrypted) {

        this.isRoot = username.equals("root");

        if (this.isRoot) {
            this.isSudoer = true;
        } else {
            this.isSudoer = isSudoer != null && isSudoer;
        }

        this.username = username;
        this.password = password;
        this.isPasswordEncrypted = isPasswordEncrypted;
    }

}
