package com.example.serverprovision.execution.engine.rhel;

import java.util.Objects;
import java.util.Optional;

/**
 * 정의서 해석 결과 — 렌더할 수 있는 목표이거나 막힌 사유다. 정의서 저장 뒤 카탈로그가 바뀌는 일은 정상 운영에서도
 * 일어나므로 막힘은 예외가 아니라 값으로 나른다(실행 준비도가 사유를 화면에 낸다, b-4).
 */
public sealed interface RhelResolution {

    /** 렌더할 수 있으면 그 결과 — 막혔으면 empty. */
    Optional<Ready> ready();

    record Ready(RhelInstallTarget target, RhelInstallMedia media) implements RhelResolution {
        public Ready {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(media, "media");
        }

        @Override
        public Optional<Ready> ready() {
            return Optional.of(this);
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

        @Override
        public Optional<Ready> ready() {
            return Optional.empty();
        }
    }
}
