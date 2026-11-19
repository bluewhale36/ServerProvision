package com.example.serverprovision.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * main 소스 전체가 소스 규칙(S1 ~ S4)과 Javadoc 규칙(J1 ~ J5)을 지키는지 본다.
 * 이미 있는 위반은 기준선에 있고, 기준선은 줄어들기만 한다. 규칙 목록과 고칠 방법은 {@link ConventionRule}.
 */
class CodingConventionTest {

	private static final Path MAIN = Path.of("src/main/java");

	@Test
	@DisplayName("main 소스가 소스 규칙과 Javadoc 규칙을 지킨다(기준선에 적힌 옛 위반만 남아 있다)")
	void mainSourceFollowsCodingRules() {
		List<ConventionViolation> violations = mainSources()
				.flatMap(file -> Stream.concat(SourceRules.check(file), JavadocRules.check(file)))
				.toList();

		ConventionBaseline.load().mismatch(ConventionRule.Engine.SOURCE, violations)
				.ifPresent(reason -> fail("소스 · Javadoc 규칙 위반" + reason));
	}

	private static Stream<SourceFile> mainSources() {
		try (Stream<Path> paths = Files.walk(MAIN)) {
			return paths.filter(p -> p.toString().endsWith(".java"))
					.sorted()
					.map(CodingConventionTest::parse)
					.toList()
					.stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static SourceFile parse(Path path) {
		try {
			return SourceFile.parse(MAIN.relativize(path).toString().replace('\\', '/'), Files.readString(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
