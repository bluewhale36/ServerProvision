package com.example.serverprovision.convention;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 이미 있는 위반을 적어 둔 기준선. 기준선은 줄어들기만 한다.
 * 새 위반이 생기거나, 고쳐서 위반이 아니게 된 줄이 남아 있으면 실패 사유를 돌려준다. 테스트는 이 파일을 읽기만 한다.
 */
final class ConventionBaseline {

	static final Path FILE = Path.of("src/test/resources/convention/baseline.txt");

	private final Set<String> keys;
	/** 탭 세 칸이 아니거나 없는 규칙 ID 인 줄. 어느 검사도 낡은 줄로 볼 수 없어 남아 있게 되므로 따로 실패시킨다. */
	private final List<String> malformed;

	ConventionBaseline(Set<String> keys) {
		this.keys = keys;
		this.malformed = keys.stream().filter(key -> key.split("\t", -1).length != 3 || ruleOf(key).isEmpty()).sorted().toList();
	}

	static ConventionBaseline load() {
		try {
			return parse(Files.readAllLines(FILE));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static ConventionBaseline parse(List<String> lines) {
		return new ConventionBaseline(lines.stream()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.collect(Collectors.toUnmodifiableSet()));
	}

	/** 한 검사의 위반 목록을 기준선과 맞춘다. 맞으면 empty, 어긋나면 고칠 일을 적은 실패 사유다. */
	Optional<String> mismatch(ConventionRule.Engine engine, Collection<ConventionViolation> violations) {
		Map<String, ConventionViolation> found = new LinkedHashMap<>();
		violations.forEach(v -> found.putIfAbsent(v.key(), v));

		List<ConventionViolation> added = found.values().stream()
				.filter(v -> !keys.contains(v.key()))
				.toList();
		List<String> stale = keys.stream()
				.filter(key -> ruleOf(key).map(rule -> rule.engine() == engine).orElse(false))
				.filter(key -> !found.containsKey(key))
				.filter(key -> !malformed.contains(key))
				.sorted()
				.toList();
		if (added.isEmpty() && stale.isEmpty() && malformed.isEmpty()) {
			return Optional.empty();
		}
		String renameHint = !added.isEmpty() && !stale.isEmpty()
				? "\n새 위반과 지울 줄이 함께 있다. 옛 위반의 이름만 바뀐 경우라도 손댄 김에 고치고 옛 줄을 지운다.\n"
				: "";
		return Optional.of(describe(added) + describeStale(stale) + renameHint + describeMalformed());
	}

	private String describeMalformed() {
		if (malformed.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder("\n형식이 틀린 기준선 줄 " + malformed.size() + " 건. "
				+ "'규칙ID<TAB>파일<TAB>멤버' 꼴이 아니거나 없는 규칙 ID 다. 고치거나 지운다.\n");
		malformed.forEach(line -> out.append(line).append('\n'));
		return out.toString();
	}

	private static Optional<ConventionRule> ruleOf(String key) {
		String id = key.substring(0, key.indexOf('\t') < 0 ? key.length() : key.indexOf('\t'));
		try {
			return Optional.of(ConventionRule.valueOf(id));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static String describe(List<ConventionViolation> added) {
		if (added.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder("\n새 위반 " + added.size() + " 건. 코드를 고친다.\n");
		Map<ConventionRule, List<ConventionViolation>> byRule = added.stream()
				.collect(Collectors.groupingBy(ConventionViolation::rule, TreeMap::new, Collectors.toList()));
		byRule.forEach((rule, list) -> {
			out.append("\n[").append(rule.name()).append("] ").append(rule.title()).append('\n');
			out.append("  고칠 방법: ").append(rule.fix()).append('\n');
			list.forEach(v -> out.append("  - ").append(v.file()).append("  ").append(v.member())
					.append(v.detail().isEmpty() ? "" : "  (" + v.detail() + ")").append('\n'));
		});
		out.append("\n고칠 수 없는 사정이 있어 기준선에 더할 때만 아래 줄을 쓰고, PR 에 사유를 적는다.\n");
		added.stream().map(ConventionViolation::key).sorted().forEach(key -> out.append(key).append('\n'));
		return out.toString();
	}

	private static String describeStale(List<String> stale) {
		if (stale.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder("\n더 이상 위반이 아닌 기준선 줄 " + stale.size() + " 건. "
				+ FILE + " 에서 지운다.\n");
		stale.forEach(key -> out.append(key).append('\n'));
		return out.toString();
	}
}
