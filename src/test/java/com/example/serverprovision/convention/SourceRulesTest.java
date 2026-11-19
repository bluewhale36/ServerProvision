package com.example.serverprovision.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 소스 규칙(S1 ~ S4)과 멤버 키 형식이 문자열 고정 예제에서 기대대로 갈리는지 본다. */
class SourceRulesTest {

	private static List<String> keys(String pkg, String body) {
		SourceFile file = SourceFile.parse("x/Sample.java", "package " + pkg + ";\nclass Sample {\n" + body + "\n}\n");
		return SourceRules.check(file).map(v -> v.rule().name() + " " + v.member()).toList();
	}

	private static String lines(int count) {
		return IntStream.range(0, count).mapToObj(i -> "  int v" + i + " = " + i + ";").collect(Collectors.joining("\n"));
	}

	private static String nested(int depth) {
		return "if (a) {\n".repeat(depth) + "run();\n" + "}\n".repeat(depth);
	}

	@Test
	@DisplayName("S1: 컨트롤러 패키지 메서드의 try 를 잡고, 다른 패키지의 try 는 통과")
	void tryInController() {
		String body = "void save() { try { run(); } catch (RuntimeException e) { } }";
		assertThat(keys("a.controller", body)).containsExactly("S1 Sample#save()");
		assertThat(keys("a.service", body)).isEmpty();
	}

	@Test
	@DisplayName("S2: 본문 101 줄은 잡고 100 줄은 통과, 긴 record 선언은 메서드로 세지 않는다")
	void longBody() {
		assertThat(keys("a", "void big() {\n" + lines(101) + "\n}")).containsExactly("S2 Sample#big()");
		assertThat(keys("a", "void fit() {\n" + lines(100) + "\n}")).isEmpty();
		String record = "record Wide(" + IntStream.range(0, 120).mapToObj(i -> "int f" + i).collect(Collectors.joining(",\n")) + ") {}";
		assertThat(keys("a", record)).isEmpty();
	}

	@Test
	@DisplayName("S3: 제어 구조 5 단계는 잡고 4 단계와 else if 사슬은 통과")
	void deepNesting() {
		assertThat(keys("a", "void deep(boolean a) {\n" + nested(5) + "}")).containsExactly("S3 Sample#deep(boolean)");
		assertThat(keys("a", "void fit(boolean a) {\n" + nested(4) + "}")).isEmpty();
		String chain = "void chain(int a) { if (a == 1) { } else if (a == 2) { } else if (a == 3) { } else if (a == 4) { } else if (a == 5) { } else { } }";
		assertThat(keys("a", chain)).isEmpty();
	}

	@Test
	@DisplayName("S4: '// Claude' 와 '@author Claude' 를 잡고, 일반 주석은 통과")
	void aiMarkers() {
		assertThat(keys("a", "// Claude\nint v;")).containsExactly("S4 Sample");
		assertThat(keys("a", "/** 값. @author Claude */\nint v;")).containsExactly("S4 Sample");
		assertThat(keys("a", "// 값을 센다\nint v;")).isEmpty();
	}

	@Test
	@DisplayName("멤버 키: 중첩 타입은 점, 생성자는 <init>, 가변 인자는 배열, 제네릭 인자는 뗀다")
	void memberKeyFormat() {
		String body = "static class Inner {\n  Inner(java.util.List<String> names, String... tags) {\n" + lines(101) + "\n  }\n}";
		assertThat(keys("a", body)).containsExactly("S2 Sample.Inner#<init>(List,String[])");
	}
}
