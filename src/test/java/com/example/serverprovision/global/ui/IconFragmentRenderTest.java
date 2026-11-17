package com.example.serverprovision.global.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * 아이콘 조각(fragments/icon)이 허용된 이름만 그리고, 허용 목록이 sprite 의 symbol 과 같은지 고정한다.
 * sprite 에 없는 이름은 브라우저가 빈 칸으로 그려 조용히 누락되므로 렌더 실패로 드러나야 한다(S22-1).
 */
class IconFragmentRenderTest {

    private SpringTemplateEngine engine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver files = new ClassLoaderTemplateResolver();
        files.setPrefix("templates/");
        files.setSuffix(".html");
        files.setTemplateMode(TemplateMode.HTML);
        files.setCheckExistence(true);
        files.setOrder(1);

        StringTemplateResolver inline = new StringTemplateResolver();
        inline.setTemplateMode(TemplateMode.HTML);
        inline.setOrder(2);

        engine = new SpringTemplateEngine();
        engine.setTemplateResolvers(Set.of(files, inline));
        // 웹 요청이 없는 엔진이라 문맥 경로를 빈 값으로 둔다 — @{/icons/...} 가 그대로 /icons/... 가 된다
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
        });
    }

    private String render(String name) {
        return engine.process("<p><svg th:replace=\"~{fragments/icon :: icon('" + name + "')}\"></svg></p>", new Context());
    }

    @Test
    @DisplayName("허용된 이름은 sprite 의 해당 symbol 을 가리키는 svg 로 그린다")
    void allowedNameRendersSpriteReference() {
        String html = render("arrow-left");

        assertThat(html).contains("class=\"n-icon\"")
                .contains("aria-hidden=\"true\"")
                .contains("href=\"/icons/sprite.svg#arrow-left\"");
    }

    @Test
    @DisplayName("sprite 에 없는 이름은 빈 칸이 아니라 렌더 실패가 된다")
    void unknownNameFailsRendering() {
        assertThatThrownBy(() -> render("arrow-up"))
                .isInstanceOf(TemplateEngineException.class);
    }

    @Test
    @DisplayName("조각의 허용 목록과 sprite 의 symbol id 가 같다")
    void allowListMatchesSpriteSymbols() throws IOException {
        Set<String> spriteIds = findAll(read("static/icons/sprite.svg"), "<symbol id=\"([^\"]+)\"");
        String fragment = read("templates/fragments/icon.html");
        Matcher list = Pattern.compile("\\{\\s*([^}]*)}\\.contains\\(name\\)").matcher(fragment);
        assertThat(list.find()).as("조각에 허용 목록이 있어야 한다").isTrue();
        Set<String> allowed = findAll(list.group(1), "'([^']+)'");

        assertThat(allowed).isNotEmpty().isEqualTo(spriteIds);
    }

    private static Set<String> findAll(String text, String regex) {
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static String read(String classpath) throws IOException {
        try (InputStream in = IconFragmentRenderTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
