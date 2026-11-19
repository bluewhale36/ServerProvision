package com.example.serverprovision.convention;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 구문 분석한 main 소스 파일 하나. 소스 규칙이 기준선 키의 파일 · 멤버 부분을 여기서 만든다.
 * 멤버 표기는 구조 규칙과 같다(타입 Outer.Inner, 메서드 Type#name(Param), 생성자 Type#&lt;init&gt;(Param), 필드 Type#name).
 */
record SourceFile(String path, CompilationUnit unit) {

	static {
		StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
	}

	static SourceFile parse(String path, String source) {
		return new SourceFile(path, StaticJavaParser.parse(source));
	}

	boolean isController() {
		return unit.getPackageDeclaration().map(p -> p.getNameAsString().contains(".controller")).orElse(false);
	}

	/** 노드를 감싸는 가장 가까운 이름 있는 타입의 키. 파일 맨 위 주석처럼 타입 밖이면 첫 타입 이름을 쓴다. */
	String typeOf(Node node) {
		Deque<String> names = new ArrayDeque<>();
		Optional<Node> current = Optional.of(node);
		while (current.isPresent()) {
			if (current.get() instanceof TypeDeclaration<?> type) {
				names.addFirst(type.getNameAsString());
			}
			current = current.get().getParentNode();
		}
		if (names.isEmpty()) {
			return unit.getTypes().isEmpty() ? path : unit.getType(0).getNameAsString();
		}
		return String.join(".", names);
	}

	/** Javadoc 이 붙는 선언의 멤버 키. 타입 선언이면 타입 키다. 키를 만들 수 없는 자리면 empty. */
	Optional<String> memberOf(Node declaration) {
		return switch (declaration) {
			case TypeDeclaration<?> type -> Optional.of(typeOf(type));
			case CallableDeclaration<?> callable -> Optional.of(callable(callable));
			case CompactConstructorDeclaration compact -> Optional.of(typeOf(compact.getParentNode().orElseThrow()) + "#<init>()");
			case FieldDeclaration field -> Optional.of(typeOf(field.getParentNode().orElseThrow()) + "#" + field.getVariable(0).getNameAsString());
			case EnumConstantDeclaration constant -> Optional.of(typeOf(constant.getParentNode().orElseThrow()) + "#" + constant.getNameAsString());
			case AnnotationMemberDeclaration member -> Optional.of(typeOf(member.getParentNode().orElseThrow()) + "#" + member.getNameAsString() + "()");
			default -> Optional.empty();
		};
	}

	String callable(CallableDeclaration<?> callable) {
		String name = callable instanceof ConstructorDeclaration ? "<init>" : callable.getNameAsString();
		String params = callable.getParameters().stream().map(SourceFile::simpleType).collect(Collectors.joining(","));
		return typeOf(callable.getParentNode().orElseThrow()) + "#" + name + "(" + params + ")";
	}

	/** 바이트코드 쪽 단순 이름과 맞춘다. 제네릭 인자와 패키지를 떼고, 가변 인자는 배열로 쓴다. */
	private static String simpleType(Parameter parameter) {
		return simpleType(parameter.getType()) + (parameter.isVarArgs() ? "[]" : "");
	}

	private static String simpleType(Type type) {
		return switch (type) {
			case ClassOrInterfaceType named -> named.getNameAsString();
			case ArrayType array -> simpleType(array.getComponentType()) + "[]";
			default -> type.asString();
		};
	}
}
