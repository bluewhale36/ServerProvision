package com.example.serverprovision.domain.os.model.installation;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public class Timezone implements InstallScriptable {

    @NotBlank(message = "시간대는 필수 입력값입니다.")
    private final String timezone;
    private final boolean isUTC;

    @Override
    public String getRHELScript() {
        StringBuilder sb = new StringBuilder();

        sb.append("timezone ").append(timezone);

        if (isUTC) sb.append(" --isUtc");

        sb.append("\n");

        return sb.toString();
    }
}
