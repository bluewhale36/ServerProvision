package com.example.serverprovision.convention;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Javadoc 의 길이와 금지 패턴(J1 ~ J5). 뜻이 한 번에 전달되는지는 기계로 판정하지 않고
 * .claude/domain-conventions/javadoc.md 로 검수한다.
 */
final class JavadocRules {

	static final int MAX_MEMBER_LINES = 4;
	static final int MAX_TYPE_LINES = 8;

	/** 저장소의 인벤토리 코드 계열로 시작하고, 몇 낱말 안에 대시나 쌍점이 오는 첫 줄. 예: "S23-1 —", "E4-1-b-2 D-21 —". */
	private static final Pattern STAGE_CODE_HEAD = Pattern.compile(
			"^(?:<p>)?\\s*(?:MA|MK|ES|HF|CH|DOC|OPS|DPL|U|E|S|R)\\d+(?:[-.][0-9A-Za-z]+)*(?:\\s+[A-Z0-9][\\w-]*){0,3}\\s*(?:—|–|:|\\s-\\s)");
	private static final Pattern ARROW = Pattern.compile("→|⇒|->|=>|-&gt;|=&gt;");
	private static final Pattern NESTED_PARENS = Pattern.compile("\\([^()]*\\([^()]*\\)[^()]*\\)");
	/** 코드 예시와 링크 대상은 문장이 아니므로 J4 · J5 에서 뺀다. 중괄호는 한 겹까지 허용한다. */
	private static final Pattern CODE_SPANS = Pattern.compile("\\{@\\w+(?:[^{}]|\\{[^{}]*\\})*\\}|<pre>.*?</pre>", Pattern.DOTALL);

	private JavadocRules() {
	}

	static Stream<ConventionViolation> check(SourceFile file) {
		return file.unit().getAllComments().stream()
				.filter(JavadocComment.class::isInstance)
				.map(JavadocComment.class::cast)
				.flatMap(javadoc -> javadoc.getCommentedNode()
						.flatMap(node -> file.memberOf(node).map(member -> violations(file, node, member, javadoc)))
						.orElseGet(Stream::empty));
	}

	private static Stream<ConventionViolation> violations(SourceFile file, Node node, String member, JavadocComment javadoc) {
		List<String> description = descriptionLines(javadoc.getContent());
		String prose = CODE_SPANS.matcher(javadoc.getContent()).replaceAll(" ");
		boolean type = node instanceof TypeDeclaration<?>;
		int limit = type ? MAX_TYPE_LINES : MAX_MEMBER_LINES;

		ConventionRule lengthRule = type ? ConventionRule.J2 : ConventionRule.J1;
		Stream<ConventionViolation> length = description.size() > limit
				? Stream.of(new ConventionViolation(lengthRule, file.path(), member, description.size() + " 줄"))
				: Stream.empty();
		Stream<ConventionViolation> patterns = Stream.of(
						Optional.of(ConventionRule.J3).filter(r -> !description.isEmpty() && STAGE_CODE_HEAD.matcher(description.getFirst()).find()),
						Optional.of(ConventionRule.J4).filter(r -> ARROW.matcher(prose).find()),
						Optional.of(ConventionRule.J5).filter(r -> NESTED_PARENS.matcher(prose).find()))
				.flatMap(Optional::stream)
				.map(rule -> new ConventionViolation(rule, file.path(), member));
		return Stream.concat(length, patterns);
	}

	/** 본문 줄. 앞의 '*' 와 빈 줄을 빼고, 첫 블록 태그(@param 등)에서 멈춘다. */
	static List<String> descriptionLines(String content) {
		return Arrays.stream(content.split("\n"))
				.map(line -> line.strip().replaceFirst("^\\*\\s?", "").strip())
				.takeWhile(line -> !line.startsWith("@"))
				.filter(line -> !line.isEmpty())
				.toList();
	}
}
