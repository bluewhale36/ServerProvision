package com.example.serverprovision.execution.engine.rhel;

import java.util.Objects;

/**
 * 정의서 해석 결과 — 렌더할 수 있는 목표이거나 막힌 사유다. 정의서 저장 뒤 카탈로그가 바뀌는 일은 정상 운영에서도
 * 일어나므로 막힘은 예외가 아니라 값으로 나른다(실행 준비도가 사유를 화면에 낸다, b-4).
 */
public sealed interface RhelResolution {

    record Ready(RhelInstallTarget target) implements RhelResolution {
        public Ready {
            Objects.requireNonNull(target, "target");
        }
    }

    /** detail = 사유가 가리키는 대상(카탈로그 id 또는 OS 화면 이름). */
    record Blocked(RhelBlockReason reason, String detail) implements RhelResolution {
        public Blocked {
            Objects.requireNonNull(reason, "reason");
        }

        public String message() {
            return reason.message(detail);
        }
    }
}
