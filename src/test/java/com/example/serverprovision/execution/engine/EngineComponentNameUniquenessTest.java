package com.example.serverprovision.execution.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * phase 마다 행 · 실행기 빈을 자기 패키지에 두는 구조(E2-2 · E3-1)에서는 단순명이 같은 클래스가 두 패키지에
 * 생기기 쉽다. Spring 기본 빈 이름은 패키지를 무시한 단순명이라 그 순간 기동이 죽는데(E3-1 CP5 F-1 —
 * {@code SkipOutOfWindowStep} 둘), 단위 테스트는 컨텍스트를 올리지 않아 잡지 못한다. 그래서 여기서 고정한다.
 */
class EngineComponentNameUniquenessTest {

    @Test
    @DisplayName("@Component 단순명은 전체 패키지에서 유일하다 — 같으면 Spring 기본 빈 이름이 충돌한다")
    void componentSimpleNamesAreUnique() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        Map<String, List<String>> bySimpleName = scanner.findCandidateComponents("com.example.serverprovision").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(Objects::nonNull)
                .collect(groupingBy(name -> name.substring(name.lastIndexOf('.') + 1)));
        List<List<String>> duplicated = bySimpleName.values().stream().filter(names -> names.size() > 1).toList();

        assertThat(duplicated)
                .as("단순명이 같은 @Component 가 둘 이상이면 기동에서 ConflictingBeanDefinitionException 이 난다")
                .isEmpty();
    }
}
