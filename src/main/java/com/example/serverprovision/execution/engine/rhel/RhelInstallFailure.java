package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.OsInstallFailureReason;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;

import java.util.Objects;
import java.util.Optional;

/** RHEL 설치에만 있는 실패 사유(E4-1-b-4-2). 둘 다 운영자가 고칠 자리를 알려 주는 문구를 화면에 그대로 낸다. */
public sealed interface RhelInstallFailure extends OsInstallFailureReason {

    /** 설치 트리를 확보하지 못했다 — 문구는 트리 사유의 것이다. */
    record TreeUnavailable(TreeFailureReason reason) implements RhelInstallFailure {
        public TreeUnavailable {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String code() {
            return "TREE_" + reason.name();
        }

        @Override
        public Optional<String> screenMessage() {
            return Optional.of(reason.message());
        }
    }

    /** 확보한 트리가 정의서와 다른 배포판이나 주 버전이다 — ISO 내용과 등록 OS 가 어긋났다. */
    record ReleaseMismatch(String treeLabel, String definitionLabel) implements RhelInstallFailure {
        public ReleaseMismatch {
            Objects.requireNonNull(treeLabel, "treeLabel");
            Objects.requireNonNull(definitionLabel, "definitionLabel");
        }

        @Override
        public String code() {
            return "TREE_RELEASE_MISMATCH";
        }

        @Override
        public Optional<String> screenMessage() {
            return Optional.of("설치 트리 버전(" + treeLabel + ")과 정의서 버전(" + definitionLabel + ")이 다릅니다. "
                    + "ISO 를 실제 OS 버전 아래에 다시 등록하거나 정의서에서 다른 ISO 를 골라야 합니다.");
        }
    }
}
