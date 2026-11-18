package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.vo.TreeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 풀린 디렉터리가 네트워크 설치에 쓸 수 있는 RHEL 계열 트리인지 확인하고 배포판 · 버전을 읽는다.
 * 필수: 트리 설명서 · 설치 커널 · 초기 램디스크 · 설치기 본체 · BaseOS 저장소 색인. 하나라도 없으면 empty.
 * 트리 설명서 해석은 관리 영역의 {@link TreeInfo} 와 같다(E4-1-b-2 — ISO 등록 때 버전 확인과 한 규칙).
 */
@Slf4j
@Component
public class InstallTreeInspector {

    static final String BASEOS_REPOMD = "BaseOS/repodata/repomd.xml";

    public Optional<TreeRelease> inspect(Path treeRoot) {
        Optional<TreeInfo> info;
        try {
            info = TreeInfo.read(treeRoot);
        } catch (UncheckedIOException e) {
            log.warn("[install-tree] 트리 설명서를 읽지 못함. root={}, msg={}", treeRoot, e.getMessage());
            return Optional.empty();
        }
        if (info.isEmpty()) {
            log.info("[install-tree] 트리 설명서 없음 또는 배포판 정보 없음 — RHEL 설치 트리 아님. root={}", treeRoot);
            return Optional.empty();
        }
        for (String required : List.of(InstallTree.KERNEL, InstallTree.INITRD, info.get().stage2(), BASEOS_REPOMD)) {
            if (!Files.isRegularFile(treeRoot.resolve(required))) {
                log.info("[install-tree] 필수 파일 없음 — RHEL 설치 트리 아님. root={}, missing={}", treeRoot, required);
                return Optional.empty();
            }
        }
        return Optional.of(new TreeRelease(info.get().name(), info.get().version()));
    }
}
