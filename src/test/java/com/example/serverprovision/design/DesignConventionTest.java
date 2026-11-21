package com.example.serverprovision.design;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DESIGN.md 의 규약 중 마크업과 CSS 만 보고 판정할 수 있는 것을 검사한다.
 * 세션마다 규약을 읽었는지와 무관하게 CP4 의 전체 테스트에서 걸리게 하려는 장치다. 실패 메시지가 규약 절을 가리킨다.
 * 렌더해야 보이는 것(겹친 구분선, 저장된 문장)은 scripts/design-audit 가 맡는다.
 */
@DisplayName("디자인 규약 (DESIGN.md 4, 5, 7, 8, 9절)")
class DesignConventionTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path STATIC = Path.of("src/main/resources/static");
    private static final Path MESSAGES = Path.of("src/main/resources/messages.properties");

    /** 폼 안 인라인 편집 표는 정의서 작성 화면에만 있다. 새 화면에는 쓰지 않는다(DESIGN.md 9절 표 타입 선택). */
    private static final Set<String> TBL_LIST_ALLOWED = Set.of("provisioning/setting-new.html");

    /**
     * 스크립트가 요소를 찾으려고 단 n- 표지(정의 없음). 새 표지는 data-* 로 단다(DESIGN.md 8절). 이 목록은 줄이기만 한다.
     */
    private static final Set<String> LEGACY_JS_HOOKS = Set.of(
            "n-browse-entry", "n-browse-entry-size", "n-btn-extract", "n-env-groups-wrap", "n-iso-section-wrap",
            "n-form-banner", "n-pkg-group", "n-subprogram-miller");

    /** 두 시트에 같은 단독 선택자가 있는 기존 중복(DESIGN.md 8절). 이 목록은 줄이기만 한다. */
    private static final Set<String> LEGACY_DOUBLE_DEFINITIONS = Set.of();

    /** 앱의 반응형 경계는 이 둘뿐이다(DESIGN.md 4절 반응형, R21 토론 Q2). */
    private static final Set<Integer> BREAKPOINTS = Set.of(1024, 640);

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("<(script|style)\\b.*?</\\1>", Pattern.DOTALL);
    private static final Pattern TAG_ATTRS = Pattern.compile("(?:[^>\"']|\"[^\"]*\"|'[^']*')*");
    private static final Pattern TABLE = Pattern.compile("<table\\b(" + TAG_ATTRS + ")>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern CLASS_ATTR = Pattern.compile("(?:\\s|^)(?:class|th:class|th:classappend)=\"([^\"]*)\"");
    private static final Pattern N_CLASS = Pattern.compile("(?<![\\w-])(n-[a-z0-9][\\w-]*)");
    private static final Pattern VISIBLE_TEXT = Pattern.compile(
            ">([^<>]+)<|\\s(?:title|placeholder|aria-label|th:text|th:title|th:placeholder|th:utext"
                    + "|data-[\\w-]*(?:message|title|label|confirm|text|reason|hint|tooltip)[\\w-]*"
                    + "|th:data-[\\w-]*(?:message|title|label|confirm|text|reason|hint|tooltip)[\\w-]*)=\"([^\"]*)\"");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern BANNED_PUNCT = Pattern.compile("[—·]");
    /** 콘솔 문구는 합니다체 평서형만 쓴다(DESIGN.md 7절 문체, 사용자 지시 2026-11-16). */
    private static final Pattern BANNED_ENDING = Pattern.compile("십시오|하세요|해\\s?주세요");

    private static Map<String, String> templates;

    @BeforeAll
    static void load() {
        templates = new TreeMap<>();
        for (Path p : files(TEMPLATES, ".html")) {
            templates.put(TEMPLATES.relativize(p).toString(), COMMENT.matcher(read(p)).replaceAll(""));
        }
    }

    @Test
    @DisplayName("표는 n-table · n-detail-table · n-tbl-list 중 하나만 쓴다 (5절 고르기)")
    void tableUsesExactlyOneKind() {
        List<String> violations = new ArrayList<>();
        forEachTable((file, classes, body) -> {
            List<String> kinds = tableKinds(classes);
            if (kinds.size() != 1 && !isFragmentHost(body)) {
                violations.add(file + " : " + (kinds.isEmpty() ? "표 클래스 없음" : String.join(" + ", kinds)));
            }
        });
        assertThat(violations)
                .as("표마다 목록(n-table), 키-값(n-detail-table), 폼 안 편집(n-tbl-list) 가운데 하나를 고른다. DESIGN.md 5절 고르기 표")
                .isEmpty();
    }

    @Test
    @DisplayName("목록 표는 thead 를 갖고, 키-값 표는 thead 를 갖지 않는다 (9절 표 타입 선택)")
    void tableStructureMatchesKind() {
        List<String> violations = new ArrayList<>();
        forEachTable((file, classes, body) -> {
            List<String> kinds = tableKinds(classes);
            if (kinds.size() != 1) {
                return;
            }
            boolean hasHead = body.contains("<thead");
            String kind = kinds.getFirst();
            if (!kind.equals("n-detail-table") && !hasHead) {
                violations.add(file + " : " + kind + " 인데 thead 가 없다. 라벨과 값의 쌍이면 n-detail-table 이다");
            }
            if (kind.equals("n-detail-table") && hasHead) {
                violations.add(file + " : n-detail-table 인데 thead 가 있다. 여러 행의 목록이면 n-table 이다");
            }
            if (kind.equals("n-tbl-list") && !TBL_LIST_ALLOWED.contains(file)) {
                violations.add(file + " : n-tbl-list 는 새로 쓰지 않는다. n-table 을 쓴다");
            }
        });
        assertThat(violations).as("DESIGN.md 9절 표 타입 선택").isEmpty();
    }

    @Test
    @DisplayName("템플릿에 인라인 스타일과 <style> 블록이 없다 (8절)")
    void noInlineStyle() {
        List<String> violations = templates.entrySet().stream()
                .filter(e -> Pattern.compile("\\s(?:th:)?style=\"|<style\\b").matcher(e.getValue()).find())
                .map(Map.Entry::getKey)
                .toList();
        assertThat(violations).as("간격과 부연은 역할 헬퍼 클래스로, 화면 규칙은 그 화면의 CSS 파일로 둔다. DESIGN.md 8절").isEmpty();
    }

    @Test
    @DisplayName("템플릿이 프로젝트 클래스의 정적 메서드를 T(...) 로 부르지 않는다 (8절)")
    void noStaticProjectMethodCallInTemplates() {
        Pattern staticCall = Pattern.compile("T\\(com\\.example\\.[\\w.]+\\)\\.\\w+\\(");
        List<String> violations = templates.entrySet().stream()
                .filter(e -> staticCall.matcher(e.getValue()).find())
                .map(Map.Entry::getKey)
                .toList();
        assertThat(violations)
                .as("devtools 의 재시작 클래스로더와 T(...) 가 찾는 클래스의 로더가 달라, 프로젝트 타입 인자를 넘기면"
                        + " 메서드를 찾지 못해 렌더가 끊긴다. 값 객체나 enum 의 인스턴스 메서드, 응답 DTO 필드를 쓴다. DESIGN.md 8절")
                .isEmpty();
    }

    @Test
    @DisplayName("마크업에 쓴 n- 클래스는 CSS 에 정의가 있다 (8절)")
    void usedClassesAreDefined() {
        Set<String> defined = definedClasses();
        Map<String, Set<String>> undefined = new TreeMap<>();
        templates.forEach((file, html) -> {
            Matcher attr = CLASS_ATTR.matcher(html);
            while (attr.find()) {
                Matcher c = N_CLASS.matcher(attr.group(1));
                while (c.find()) {
                    String name = c.group(1);
                    if (name.endsWith("-") || defined.contains(name) || LEGACY_JS_HOOKS.contains(name)) {
                        continue;   // 'n-badge-' + 값 처럼 식으로 이어 붙이는 접두는 판정하지 않는다
                    }
                    undefined.computeIfAbsent(name, k -> new TreeSet<>()).add(file);
                }
            }
        });
        assertThat(undefined)
                .as("정의 없는 클래스는 아무 일도 하지 않는다. 있는 컴포넌트를 쓰거나 style.css 에 먼저 정의한다."
                        + " 스크립트 표지는 data-* 로 단다. DESIGN.md 8절")
                .isEmpty();
    }

    @Test
    @DisplayName("한 클래스의 단독 선택자는 한 시트에만 있다 (8절)")
    void singleDefinitionPerClass() {
        Map<String, Set<String>> owners = new TreeMap<>();
        Pattern bare = Pattern.compile("(?:^|[},])\\s*\\.(n-[\\w-]+)(?::[\\w-]+(?:\\([^)]*\\))?)*\\s*(?=[,{])");
        for (Path css : cssFiles()) {
            String body = CSS_COMMENT.matcher(read(css)).replaceAll("");
            body = Pattern.compile("@media[^{]*\\{").matcher(body).replaceAll("");
            // :is(), :where(), :not(), :has() 괄호 안의 선택자는 단독 선택자가 아니다
            String previous;
            do {
                previous = body;
                body = body.replaceAll("\\([^(){}]*\\)", "");
            } while (!body.equals(previous));
            Matcher m = bare.matcher(body);
            while (m.find()) {
                owners.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(STATIC.relativize(css).toString());
            }
        }
        Map<String, Set<String>> doubled = new LinkedHashMap<>();
        owners.forEach((name, files) -> {
            if (files.size() > 1 && !LEGACY_DOUBLE_DEFINITIONS.contains(name)) {
                doubled.put(name, files);
            }
        });
        assertThat(doubled)
                .as("같은 단독 선택자가 두 시트에 있으면 적재 순서가 결과를 정한다. 정의를 한 파일로 모은다. DESIGN.md 8절")
                .isEmpty();
    }

    @Test
    @DisplayName("화면 문구에 대시, 가운뎃점, 하십시오체가 없다 (7절 문체 · 표기)")
    void visibleCopyHasNoBannedPunctuation() {
        List<String> violations = new ArrayList<>();
        templates.forEach((file, html) -> {
            Matcher m = VISIBLE_TEXT.matcher(SCRIPT_OR_STYLE.matcher(html).replaceAll(""));
            while (m.find()) {
                String text = m.group(1) != null ? m.group(1) : m.group(2);
                if (text != null && HANGUL.matcher(text).find()
                        && (BANNED_PUNCT.matcher(text).find() || BANNED_ENDING.matcher(text).find())) {
                    violations.add(file + " : " + text.strip());
                }
            }
        });
        for (String line : read(MESSAGES).split("\n")) {
            if (line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String value = line.substring(line.indexOf('=') + 1);
            if (BANNED_PUNCT.matcher(value).find() || BANNED_ENDING.matcher(value).find()) {
                violations.add("messages.properties : " + line.strip());
            }
        }
        assertThat(violations)
                .as("문장은 합니다체 평서형('~해야 합니다.')으로 쓰고, 콜론, 마침표로 잇고, 나열은 쉼표로 한다. DESIGN.md 7절 문체와 표기")
                .isEmpty();
    }

    @Test
    @DisplayName("막힌 버튼에 title 을 달지 않는다 (5절 버튼, 7절 위계 표의 차단 사유 행)")
    void disabledButtonHasNoTitle() {
        Pattern button = Pattern.compile("<button\\b(" + TAG_ATTRS + ")>");
        Pattern disabled = Pattern.compile("\\s(?:th:)?disabled\\b");
        Pattern title = Pattern.compile("\\s(?:th:)?title=");
        List<String> violations = new ArrayList<>();
        templates.forEach((file, html) -> {
            Matcher m = button.matcher(html);
            while (m.find()) {
                String attrs = m.group(1);
                if (disabled.matcher(attrs).find() && title.matcher(attrs).find()) {
                    violations.add(file + " : " + attrs.strip().replaceAll("\\s+", " "));
                }
            }
        });
        assertThat(violations)
                .as("브라우저는 disabled 버튼의 title 을 띄우지 않는다. 사유는 버튼을 n-btn-tooltip-wrap[data-tooltip] 으로 감싸"
                        + " 버튼 아래 글자로 보이거나, 곁 글자가 이미 있으면 title 만 지운다. DESIGN.md 5절 버튼")
                .isEmpty();
    }

    @Test
    @DisplayName("반응형 경계는 max-width 1024 와 640 둘뿐이다 (4절 반응형)")
    void breakpointsAreOnly1024And640() {
        Pattern maxWidth = Pattern.compile("@media[^{]*max-width:\\s*(\\d+)px");
        Map<String, Set<String>> violations = new TreeMap<>();
        for (Path css : cssFiles()) {
            Matcher m = maxWidth.matcher(CSS_COMMENT.matcher(read(css)).replaceAll(""));
            while (m.find()) {
                if (!BREAKPOINTS.contains(Integer.parseInt(m.group(1)))) {
                    violations.computeIfAbsent(STATIC.relativize(css).toString(), k -> new TreeSet<>()).add(m.group(1));
                }
            }
        }
        assertThat(violations)
                .as("경계가 늘면 화면마다 확인할 폭이 는다. 1024 이하는 사이드바가 머리 줄과 서랍이 되는 폭,"
                        + " 640 이하는 휴대폰 배치다. 가까운 쪽으로 흡수한다. DESIGN.md 4절 반응형")
                .isEmpty();
    }

    // ---- 도우미 ----

    @FunctionalInterface
    private interface TableVisitor {
        void visit(String file, String classes, String body);
    }

    private static void forEachTable(TableVisitor visitor) {
        templates.forEach((file, html) -> {
            Matcher t = TABLE.matcher(html);
            while (t.find()) {
                StringBuilder classes = new StringBuilder();
                Matcher c = CLASS_ATTR.matcher(" " + t.group(1));
                while (c.find()) {
                    classes.append(' ').append(c.group(1));
                }
                visitor.visit(file, classes.toString(), t.group(2));
            }
        });
    }

    private static List<String> tableKinds(String classes) {
        return Stream.of("n-table", "n-detail-table", "n-tbl-list")
                .filter(k -> Pattern.compile("(?<![\\w-])" + k + "(?![\\w-])").matcher(classes).find())
                .toList();
    }

    /** 조각 파일이 행 조각을 정의하려고 둔 표 껍데기(<table><tbody><tr th:fragment=...>)는 렌더되지 않는다. */
    private static boolean isFragmentHost(String body) {
        return body.contains("th:fragment");
    }

    private static Set<String> definedClasses() {
        Set<String> defined = new TreeSet<>();
        Pattern selectorClass = Pattern.compile("\\.(n-[\\w-]+)");
        for (Path css : cssFiles()) {
            String body = CSS_COMMENT.matcher(read(css)).replaceAll("");
            Matcher block = Pattern.compile("([^{}]+)\\{").matcher(body);
            while (block.find()) {
                Matcher m = selectorClass.matcher(block.group(1));
                while (m.find()) {
                    defined.add(m.group(1));
                }
            }
        }
        return defined;
    }

    private static List<Path> cssFiles() {
        return files(STATIC, ".css").stream()
                .filter(p -> !p.toString().contains("/vendor/") && !p.toString().contains("/fonts/"))
                .toList();
    }

    private static List<Path> files(Path root, String suffix) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
