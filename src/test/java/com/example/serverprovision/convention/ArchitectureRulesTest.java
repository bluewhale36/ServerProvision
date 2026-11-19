package com.example.serverprovision.convention;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 구조 규칙(A1 ~ A7)이 고정 예제에서 위반과 통과를 기대대로 가르는지 본다. 예제는 convention.fixture 패키지에 있다. */
class ArchitectureRulesTest {

	private static final String FIXTURE = "com/example/serverprovision/convention/fixture/";
	private static List<String> keys;

	@BeforeAll
	static void importFixtures() {
		keys = ArchitectureRules.check(new ClassFileImporter().importPackages("com.example.serverprovision.convention.fixture"))
				.stream().map(ConventionViolation::key).toList();
	}

	private static String key(ConventionRule rule, String file, String member) {
		return rule.name() + "\t" + FIXTURE + file + "\t" + member;
	}

	@Test
	@DisplayName("A1 · A2 · A3: 컨트롤러의 @Transactional(클래스 · 메서드), 저장소 · 엔티티 의존을 잡고 깨끗한 컨트롤러는 통과")
	void controllerRules() {
		String file = "sample/controller/SampleControllers.java";
		assertThat(keys).contains(
				key(ConventionRule.A1, file, "TransactionalController"),
				key(ConventionRule.A1, file, "LeakyController#save(String,int[])"),
				key(ConventionRule.A2, file, "LeakyController"),
				key(ConventionRule.A3, file, "LeakyController"));
		assertThat(keys).noneMatch(k -> k.endsWith("\tCleanController"));
	}

	@Test
	@DisplayName("A4: 요청 패키지의 Request 아닌 이름과 DTO 접미사를 잡고, Request 로 끝나는 이름은 통과")
	void dtoNaming() {
		String file = "sample/dto/request/SampleDtos.java";
		assertThat(keys).contains(key(ConventionRule.A4, file, "SampleQuery"), key(ConventionRule.A4, file, "SampleRequestDto"));
		assertThat(keys).doesNotContain(key(ConventionRule.A4, file, "SampleRequest"));
	}

	@Test
	@DisplayName("A4: 중첩 record 는 이름 규칙에서 빼고 최상위만 본다")
	void dtoNamingTopLevelOnly() {
		assertThat(keys).noneMatch(k -> k.contains("MacValue"));
	}

	@Test
	@DisplayName("A5: management 서비스의 TypedNameVerifier · ObjectProvider 주입을 잡고, 스캐너 구현체는 뺀다")
	void managementServiceInjection() {
		String file = "management/sample/service/SampleServices.java";
		assertThat(keys).contains(key(ConventionRule.A5, file, "InjectingService"));
		assertThat(keys).doesNotContain(key(ConventionRule.A5, file, "SampleScanner"));
	}

	@Test
	@DisplayName("A6: 엔티티 · DTO 의 String mac · ip 를 잡고, 정적 상수와 값 객체는 통과")
	void networkValuesAreTyped() {
		assertThat(keys).contains(
				key(ConventionRule.A6, "sample/entity/SampleEntity.java", "SampleEntity#mac"),
				key(ConventionRule.A6, "sample/dto/response/NetworkResponses.java", "NetworkResponse#macAddress"),
				key(ConventionRule.A6, "sample/dto/response/NetworkResponses.java", "NetworkResponse#ipAddress"));
		assertThat(keys).doesNotContain(
				key(ConventionRule.A6, "sample/entity/SampleEntity.java", "SampleEntity#ip"),
				key(ConventionRule.A6, "sample/dto/response/NetworkResponses.java", "TypedNetworkResponse#mac"));
	}

	@Test
	@DisplayName("A7: 대문자가 든 패키지를 잡는다")
	void lowercasePackage() {
		assertThat(keys).contains(key(ConventionRule.A7, "Upper/UpperPackageHolder.java", "UpperPackageHolder"));
	}
}
