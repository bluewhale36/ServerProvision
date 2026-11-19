package com.example.serverprovision.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 기준선 대조가 새 위반과 낡은 줄을 모두 실패로 알리고, 다른 검사의 줄은 건드리지 않는지 본다. */
class ConventionBaselineTest {

	private static final ConventionViolation OLD = new ConventionViolation(ConventionRule.S2, "a/Big.java", "Big#run()", "120 줄");
	private static final ConventionViolation NEW = new ConventionViolation(ConventionRule.S1, "a/controller/Web.java", "Web#save()");

	private static ConventionBaseline baseline(String... lines) {
		return ConventionBaseline.parse(List.of(lines));
	}

	@Test
	@DisplayName("기준선과 위반이 같으면 통과(주석 줄과 빈 줄은 무시)")
	void matching() {
		assertThat(baseline("# 설명", "", OLD.key()).mismatch(ConventionRule.Engine.SOURCE, List.of(OLD))).isEmpty();
	}

	@Test
	@DisplayName("새 위반은 규칙 이름 · 고칠 방법 · 위치와 함께 실패")
	void newViolationFails() {
		assertThat(baseline(OLD.key()).mismatch(ConventionRule.Engine.SOURCE, List.of(OLD, NEW)))
				.hasValueSatisfying(reason -> assertThat(reason)
						.contains("새 위반 1 건", "[S1] " + ConventionRule.S1.title(), ConventionRule.S1.fix(), "a/controller/Web.java  Web#save()")
						.doesNotContain("Big#run()"));
	}

	@Test
	@DisplayName("고쳐서 위반이 아니게 된 기준선 줄은 지울 줄로 실패")
	void staleLineFails() {
		assertThat(baseline(OLD.key()).mismatch(ConventionRule.Engine.SOURCE, List.of()))
				.hasValueSatisfying(reason -> assertThat(reason).contains("더 이상 위반이 아닌 기준선 줄 1 건", OLD.key()));
	}

	@Test
	@DisplayName("다른 검사(구조 규칙)의 기준선 줄은 소스 검사에서 낡은 줄로 보지 않는다")
	void otherEngineLinesIgnored() {
		String architectureLine = "A3\ta/controller/Web.java\tWeb";
		assertThat(baseline(OLD.key(), architectureLine).mismatch(ConventionRule.Engine.SOURCE, List.of(OLD))).isEmpty();
	}

	@Test
	@DisplayName("탭 대신 공백으로 쓴 줄과 없는 규칙 ID 줄은 형식 오류로 실패(조용히 남지 않는다)")
	void malformedLinesFail() {
		assertThat(baseline(OLD.key(), "S2 a/Big.java Big#run()", "X9\ta/Big.java\tBig").mismatch(ConventionRule.Engine.SOURCE, List.of(OLD)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("형식이 틀린 기준선 줄 2 건", "S2 a/Big.java Big#run()", "X9\ta/Big.java\tBig"));
	}

	@Test
	@DisplayName("칸 수가 틀린 줄은 규칙 ID 가 맞아도 형식 오류로만 한 번 알린다(지울 줄로 겹쳐 내지 않는다)")
	void malformedLineReportedOnce() {
		assertThat(baseline("S2\ta/Big.java").mismatch(ConventionRule.Engine.SOURCE, List.of()))
				.hasValueSatisfying(reason -> assertThat(reason).contains("형식이 틀린 기준선 줄 1 건").doesNotContain("더 이상 위반이 아닌"));
	}

	@Test
	@DisplayName("새 위반과 지울 줄이 함께 있으면 이름만 바뀐 옛 위반일 수 있다고 안내한다")
	void renameHint() {
		assertThat(baseline(OLD.key()).mismatch(ConventionRule.Engine.SOURCE, List.of(NEW)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("옛 위반의 이름만 바뀐 경우"));
	}

	@Test
	@DisplayName("같은 키의 위반이 여럿이면 한 건으로 센다")
	void duplicateKeysCountOnce() {
		assertThat(baseline().mismatch(ConventionRule.Engine.SOURCE, List.of(NEW, NEW)))
				.hasValueSatisfying(reason -> assertThat(reason).contains("새 위반 1 건"));
	}
}
