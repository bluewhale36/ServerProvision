package com.example.serverprovision.maintenance.trash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-5 — 휴지통 영역 (list / settings / purge-log) 의 템플릿에서 이모지가 모두 제거되었는지 검증.
 *
 * <p>본 테스트는 Thymeleaf 렌더링이 아닌 <strong>원본 템플릿 파일</strong> 의 텍스트를 직접 검사한다.
 * 사용자 동작 시나리오가 아닌 디자인 톤 정합화이므로 mock 부담 없이 파일 단위 검증으로 충분.</p>
 *
 * <p>검증 대상 이모지 : 본 슬라이스에서 제거 결정된 6 종 (운영 설정 / 감사 로그 / cron preset / 고급 설정).</p>
 */
class TrashTemplateEmojiFreeTest {

    private static final String TEMPLATE_ROOT = "src/main/resources/templates/maintenance/trash/";

    /** Notion 본문 명시 — 휴지통 list / 운영 설정 / 감사 로그 페이지의 버튼 이모지 모두 제거. */
    private static final String[] FORBIDDEN_EMOJIS = {
            "⚙",       // ⚙ (gear)
            "📋", // 📋 (clipboard)
            "⏱",       // ⏱ (stopwatch)
            "🔧", // 🔧 (wrench)
            "🗑"  // 🗑 (wastebasket)
    };

    @ParameterizedTest(name = "{0} — 이모지 부재")
    @ValueSource(strings = { "list.html", "settings.html", "purge-log.html" })
    @DisplayName("S5-5 — 휴지통 영역 템플릿에서 이모지 전수 제거")
    void no_emoji_in_trash_templates(String fileName) throws Exception {
        String content = Files.readString(Path.of(TEMPLATE_ROOT + fileName));
        for (String emoji : FORBIDDEN_EMOJIS) {
            assertThat(content)
                    .as("%s 에 이모지 %s 가 남아있음", fileName, emoji)
                    .doesNotContain(emoji);
        }
    }
}
