package com.example.serverprovision.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserFacingMessageTest {

    @Test
    @DisplayName("문장 끝 내부 식별자를 걷어낸다")
    void stripsTrailingIds() {
        assertThat(UserFacingMessage.withoutInternalIds("게스트 서버를 찾을 수 없습니다. id=01a1bb46-10e3-71db"))
                .isEqualTo("게스트 서버를 찾을 수 없습니다.");
        assertThat(UserFacingMessage.withoutInternalIds("이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요) guestId=01a1c01a-65a3"))
                .isEqualTo("이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)");
        assertThat(UserFacingMessage.withoutInternalIds("비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도) definitionId=12"))
                .isEqualTo("비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)");
    }

    @Test
    @DisplayName("식별자가 없거나 null 이면 그대로")
    void untouched() {
        assertThat(UserFacingMessage.withoutInternalIds("입력한 값이 UUID 끝자리와 다릅니다.")).isEqualTo("입력한 값이 UUID 끝자리와 다릅니다.");
        assertThat(UserFacingMessage.withoutInternalIds(null)).isNull();
    }

    @Test
    @DisplayName("오류 응답 레코드는 만들어질 때 식별자를 걷어낸다")
    void apiErrorResponseStrips() {
        assertThat(new ApiErrorResponse("세팅 정의서를 찾을 수 없습니다. id=7").message()).isEqualTo("세팅 정의서를 찾을 수 없습니다.");
    }
}
