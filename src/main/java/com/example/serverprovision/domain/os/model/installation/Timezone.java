package com.example.serverprovision.domain.os.model.installation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

@Getter
public class Timezone implements InstallScriptable {

    @NotBlank(message = "시간대는 필수 입력값입니다.")
    private final String timezone;
    private final boolean isUTC;

    @Builder
    @JsonCreator
    Timezone(
            @JsonProperty("timezone") String timezone,
            // Jackson 이 isUTC() getter 에서 "is" 접두사를 제거해 "UTC" 로 직렬화함
            @JsonProperty("UTC")      boolean isUTC) {
        this.timezone = timezone;
        this.isUTC    = isUTC;
    }

    @Override
    public String getRHELScript() {
        StringBuilder sb = new StringBuilder();

        sb.append("timezone ").append(timezone);

        if (isUTC) sb.append(" --utc");

        sb.append("\n");

        return sb.toString();
    }
}
