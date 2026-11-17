package com.example.serverprovision.execution.installtree.vo;

import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;

import java.util.Objects;

/**
 * 설치 작업 트리 확보 결과. 실패도 예외가 아니라 값이다 — 실행기(b-4)는 준비됨이면 서빙, 준비 중이면 대기,
 * 실패면 OS 설치 단계를 실패로 닫는다(plan D-10).
 */
public sealed interface TreeAcquisition {

    record Ready(InstallTree tree) implements TreeAcquisition {
        public Ready {
            Objects.requireNonNull(tree, "tree");
        }
    }

    record Preparing() implements TreeAcquisition {
    }

    record Failed(TreeFailureReason reason) implements TreeAcquisition {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    static TreeAcquisition ready(InstallTree tree) {
        return new Ready(tree);
    }

    static TreeAcquisition preparing() {
        return new Preparing();
    }

    static TreeAcquisition failed(TreeFailureReason reason) {
        return new Failed(reason);
    }
}
