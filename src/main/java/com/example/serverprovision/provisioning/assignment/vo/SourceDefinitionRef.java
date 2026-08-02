package com.example.serverprovision.provisioning.assignment.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할당 스냅샷이 가리키는 원본 세팅 정의서의 <b>소프트참조</b>.
 *
 * <p>{@code definitionId} 는 순수 {@code Long} 스칼라다 — {@code @JoinColumn}/FK 를 절대 걸지 않는다.
 * 원본 정의서가 purge 돼도 소비 스냅샷은 생존해야 하기 때문이다(DEC-18 · 결정 D-C 자족성). {@code definitionName}
 * 은 할당 시점의 표시명 denormalized 복사 — 이후 정의서 rename/삭제와 무관하게 이력에 고정된다.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceDefinitionRef {

    /** 소프트참조 — FK 아님(순수 스칼라). */
    @Column(name = "definition_id", nullable = false, updatable = false)
    private Long definitionId;

    /** 할당 시점 표시명 스냅샷. */
    @Column(name = "definition_name", nullable = false, length = 128, updatable = false)
    private String definitionName;

    public SourceDefinitionRef(Long definitionId, String definitionName) {
        if (definitionId == null) {
            throw new IllegalArgumentException("정의서 소프트참조 id 는 null 일 수 없습니다.");
        }
        this.definitionId = definitionId;
        this.definitionName = definitionName;
    }
}
