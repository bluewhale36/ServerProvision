package com.example.serverprovision.convention;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 컴파일된 클래스로 판정하는 구조 규칙(A1 ~ A7). 규칙마다 클래스 하나를 받아 위반을 내는 함수 하나다.
 * 의존은 import 문이 아니라 바이트코드의 실제 참조로 본다.
 */
final class ArchitectureRules {

	private static final String ROOT = "com.example.serverprovision";
	private static final Set<String> TRANSACTIONAL = Set.of(
			"org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");
	private static final Set<String> NETWORK_FIELD_NAMES = Set.of("mac", "macAddress", "ip", "ipAddress");
	private static final String OBJECT_PROVIDER = "org.springframework.beans.factory.ObjectProvider";

	private static final List<Function<JavaClass, Stream<ConventionViolation>>> RULES = List.of(
			ArchitectureRules::noTransactionalInController,
			c -> controllerDependsOn(c, ".repository", ConventionRule.A2),
			c -> controllerDependsOn(c, ".entity", ConventionRule.A3),
			ArchitectureRules::dtoNaming,
			ArchitectureRules::managementServiceInjection,
			ArchitectureRules::networkValuesAreTyped,
			ArchitectureRules::lowercasePackage);

	private ArchitectureRules() {
	}

	static List<ConventionViolation> check(JavaClasses classes) {
		return classes.stream()
				.filter(ArchitectureRules::isNamedClass)
				.flatMap(c -> RULES.stream().flatMap(rule -> rule.apply(c)))
				.toList();
	}

	/** 익명 · 지역 클래스는 바깥 메서드에 속하므로 따로 판정하지 않는다. */
	private static boolean isNamedClass(JavaClass c) {
		return !c.isAnonymousClass() && !c.isLocalClass();
	}

	private static Stream<ConventionViolation> noTransactionalInController(JavaClass c) {
		if (!isController(c)) {
			return Stream.empty();
		}
		Stream<ConventionViolation> onClass = isTransactional(c.getAnnotations().stream().map(a -> a.getRawType().getName()))
				? Stream.of(new ConventionViolation(ConventionRule.A1, MemberKeys.file(c), MemberKeys.type(c)))
				: Stream.empty();
		Stream<ConventionViolation> onMethods = c.getCodeUnits().stream()
				.filter(unit -> isTransactional(unit.getAnnotations().stream().map(a -> a.getRawType().getName())))
				.map(unit -> new ConventionViolation(ConventionRule.A1, MemberKeys.file(c), MemberKeys.codeUnit(unit)));
		return Stream.concat(onClass, onMethods);
	}

	private static Stream<ConventionViolation> controllerDependsOn(JavaClass c, String packagePart, ConventionRule rule) {
		if (!isController(c)) {
			return Stream.empty();
		}
		String targets = dependencyNames(c, target -> target.getPackageName().startsWith(ROOT)
				&& target.getPackageName().contains(packagePart));
		return targets.isEmpty()
				? Stream.empty()
				: Stream.of(new ConventionViolation(rule, MemberKeys.file(c), MemberKeys.type(c), targets));
	}

	private static Stream<ConventionViolation> dtoNaming(JavaClass c) {
		String name = c.getSimpleName();
		boolean dtoSuffix = name.endsWith("DTO") || name.endsWith("Dto");
		boolean wrongSuffix = c.isTopLevelClass()
				&& (wrongSuffix(c, ".dto.request", "Request") || wrongSuffix(c, ".dto.response", "Response"));
		return dtoSuffix || wrongSuffix
				? Stream.of(new ConventionViolation(ConventionRule.A4, MemberKeys.file(c), MemberKeys.type(c)))
				: Stream.empty();
	}

	private static boolean wrongSuffix(JavaClass c, String packagePart, String suffix) {
		return c.getPackageName().contains(packagePart) && !c.getSimpleName().endsWith(suffix);
	}

	private static Stream<ConventionViolation> managementServiceInjection(JavaClass c) {
		String pkg = c.getPackageName();
		if (!pkg.contains(".management.") || !pkg.contains(".service") || isMarkableScanner(c)) {
			return Stream.empty();
		}
		String targets = dependencyNames(c, target -> target.getSimpleName().equals("TypedNameVerifier")
				|| target.getSimpleName().endsWith("MarkableScanner")
				|| target.getName().equals(OBJECT_PROVIDER));
		return targets.isEmpty()
				? Stream.empty()
				: Stream.of(new ConventionViolation(ConventionRule.A5, MemberKeys.file(c), MemberKeys.type(c), targets));
	}

	private static boolean isMarkableScanner(JavaClass c) {
		return c.getSimpleName().endsWith("MarkableScanner")
				|| c.getAllRawInterfaces().stream().anyMatch(i -> i.getSimpleName().endsWith("MarkableScanner"));
	}

	/** Lombok 이 만든 빌더는 원본 필드를 그대로 옮긴 것이라 원본 쪽에서 한 번만 센다. */
	private static Stream<ConventionViolation> networkValuesAreTyped(JavaClass c) {
		String pkg = c.getPackageName();
		boolean inScope = pkg.contains(".entity") || pkg.contains(".dto");
		if (!inScope || (c.isNestedClass() && c.getSimpleName().endsWith("Builder"))) {
			return Stream.empty();
		}
		return c.getFields().stream()
				.filter(f -> !f.getModifiers().contains(JavaModifier.STATIC))
				.filter(f -> NETWORK_FIELD_NAMES.contains(f.getName()))
				.filter(f -> f.getRawType().getName().equals(String.class.getName()))
				.map(f -> new ConventionViolation(ConventionRule.A6, MemberKeys.file(c), MemberKeys.field(f)));
	}

	private static Stream<ConventionViolation> lowercasePackage(JavaClass c) {
		return c.getPackageName().equals(c.getPackageName().toLowerCase())
				? Stream.empty()
				: Stream.of(new ConventionViolation(ConventionRule.A7, MemberKeys.file(c), MemberKeys.type(c)));
	}

	private static boolean isController(JavaClass c) {
		return c.getPackageName().contains(".controller");
	}

	private static boolean isTransactional(Stream<String> annotationNames) {
		return annotationNames.anyMatch(TRANSACTIONAL::contains);
	}

	private static String dependencyNames(JavaClass c, Predicate<JavaClass> target) {
		return c.getDirectDependenciesFromSelf().stream()
				.map(d -> d.getTargetClass().getBaseComponentType())
				.filter(target)
				.map(JavaClass::getSimpleName)
				.distinct()
				.sorted()
				.collect(Collectors.joining(", "));
	}

	/** ArchUnit 클래스 모델에서 기준선 키의 파일 · 멤버 부분을 만든다. */
	static final class MemberKeys {

		private MemberKeys() {
		}

		static String file(JavaClass c) {
			String source = c.getSourceCodeLocation().getSourceFileName();
			return c.getPackageName().replace('.', '/') + "/" + source;
		}

		static String type(JavaClass c) {
			String name = c.getName();
			return name.substring(c.getPackageName().length() + 1).replace('$', '.');
		}

		static String codeUnit(JavaCodeUnit unit) {
			String name = unit.isConstructor() ? "<init>" : unit.getName();
			String params = unit.getRawParameterTypes().stream()
					.map(JavaClass::getSimpleName)
					.collect(Collectors.joining(","));
			return type(unit.getOwner()) + "#" + name + "(" + params + ")";
		}

		static String field(JavaField f) {
			return type(f.getOwner()) + "#" + f.getName();
		}
	}
}
