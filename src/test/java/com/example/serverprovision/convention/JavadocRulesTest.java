package com.example.serverprovision.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Javadoc 규칙(J1 ~ J5)이 고정 예제에서 기대대로 갈리는지 본다. javadoc.md 의 좋은 예 · 나쁜 예를 그대로 쓴다. */
class JavadocRulesTest {

	private static List<String> rules(String javadocAndMember) {
		SourceFile file = SourceFile.parse("x/Sample.java", "package a;\nclass Sample {\n" + javadocAndMember + "\n}\n");
		return JavadocRules.check(file).map(v -> v.rule().name()).toList();
	}

	private static List<String> typeRules(String javadoc) {
		SourceFile file = SourceFile.parse("x/Sample.java", "package a;\n" + javadoc + "\nclass Sample {\n}\n");
		return JavadocRules.check(file).map(v -> v.rule().name()).toList();
	}

	@Test
	@DisplayName("J1: 멤버 본문 5 줄은 잡고, 4 줄에 @param 줄이 더해진 것은 통과")
	void memberLength() {
		assertThat(rules("/**\n * 1\n * 2\n * 3\n * 4\n * 5\n */\nvoid run() {}")).containsExactly("J1");
		assertThat(rules("/**\n * 1\n * 2\n *\n * 3\n * 4\n * @param a 값\n * @param b 값\n */\nvoid run(int a, int b) {}")).isEmpty();
	}

	@Test
	@DisplayName("J2: 타입 본문 9 줄은 잡고 8 줄은 통과")
	void typeLength() {
		assertThat(typeRules("/**\n" + " * 줄\n".repeat(9) + " */")).containsExactly("J2");
		assertThat(typeRules("/**\n" + " * 줄\n".repeat(8) + " */")).isEmpty();
	}

	@Test
	@DisplayName("J3 · J4: 단계 코드로 시작하고 화살표로 잇는 나쁜 예 1 은 둘 다 잡고, 좋은 예 1 은 통과")
	void stageCodeAndArrow() {
		assertThat(rules("/** S23-1 — 원장 origin → 판정 → 409. */\nvoid judge() {}")).containsExactly("J3", "J4");
		assertThat(rules("/** 원장 출처가 다른 설치 기록이면 409 로 거절한다. */\nvoid judge() {}")).isEmpty();
	}

	@Test
	@DisplayName("J3: 단계 코드와 결정 번호가 이어진 첫머리를 잡고, 본문 중간의 단계 코드나 비슷한 약어는 통과")
	void stageCodeHead() {
		assertThat(rules("/** E4-1-b-2 D-21 — 메이저를 고른다. */\nint v;")).containsExactly("J3");
		assertThat(rules("/** MK2 WAVE 1 — 폼을 보낸다. */\nint v;")).containsExactly("J3");
		assertThat(rules("/** 메이저를 고른다. S23-1 결정을 따른다. */\nint v;")).isEmpty();
		assertThat(rules("/** UTF8: 인코딩 이름. */\nint v;")).isEmpty();
		assertThat(rules("/** M2 SSD — 장치를 고른다. */\nint v;")).isEmpty();
	}

	@Test
	@DisplayName("J4 · J5: {@code} · {@link} · <pre> 안의 화살표와 괄호는 검사하지 않는다")
	void codeSpansExcluded() {
		assertThat(rules("/** 상태를 꺼낸다. 예: {@code ledger -> ledger.state()} */\nvoid state() {}")).isEmpty();
		assertThat(rules("/** 저장소를 쓴다({@link Sample#run(String)} 참고). */\nvoid run(String a) {}")).isEmpty();
		assertThat(rules("/** 예:\n * <pre>a -> b(c(d))</pre>\n */\nvoid run() {}")).isEmpty();
	}

	@Test
	@DisplayName("J5: 경위를 늘어놓은 나쁜 예 2 의 괄호 중첩을 잡고, 좋은 예 2 는 통과")
	void nestedParens() {
		assertThat(rules("/**\n * 처음에는 컨트롤러에서 처리했으나 R4 에서 옮겼고, 이후 HF12 에서 실기 중 null 이 들어와\n"
				+ " * (BMC 가 (가끔) 빈 값을 줌) 이렇게 바꿨다. 이 메서드는 값을 확인한다.\n */\nvoid check() {}")).containsExactly("J5");
		assertThat(rules("/** BMC 가 빈 시리얼을 주면 null 로 둔다. 빈 문자열로 저장하면 다른 서버와 같은 시리얼로 보인다. */\nvoid check() {}")).isEmpty();
	}
}
