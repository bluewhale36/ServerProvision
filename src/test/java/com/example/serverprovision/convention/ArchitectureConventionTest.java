package com.example.serverprovision.convention;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * main 코드 전체가 구조 규칙(A1 ~ A7)을 지키는지 본다. 이미 있는 위반은 기준선에 있고, 기준선은 줄어들기만 한다.
 * 규칙 목록과 고칠 방법은 {@link ConventionRule}.
 */
class ArchitectureConventionTest {

	@Test
	@DisplayName("main 코드가 구조 규칙을 지킨다(기준선에 적힌 옛 위반만 남아 있다)")
	void mainCodeFollowsArchitectureRules() {
		JavaClasses classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
				.importPackages("com.example.serverprovision");

		ConventionBaseline.load().mismatch(ConventionRule.Engine.ARCHITECTURE, ArchitectureRules.check(classes))
				.ifPresent(reason -> fail("구조 규칙 위반" + reason));
	}
}
