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

    @Test
    @DisplayName("하다 동사의 요청형(하십시오, 하세요)은 평서형으로 바꾼다. 원장에 저장된 옛 문장 대응")
    void hadaRequestBecomesDeclarative() {
        assertThat(UserFacingMessage.punctuate("자원을 되살린 뒤 재시도하십시오."))
                .isEqualTo("자원을 되살린 뒤 재시도해야 합니다.");
        assertThat(UserFacingMessage.punctuate("결과를 확인하세요."))
                .isEqualTo("결과를 확인해야 합니다.");
    }
}
