package com.example.serverprovision.execution.engine.ubuntu;

import com.example.serverprovision.execution.installtree.vo.InstallTree;

import java.util.Objects;

/**
 * Ubuntu 서빙 토큰 하나가 가리키는 내용. 커널 · initrd 는 작업 트리에서, ISO 는 등록 원본({@link InstallTree#source()})에서 낸다.
 *
 * @param tree 확보한 작업 트리
 */
public record UbuntuServing(InstallTree tree) {

    public UbuntuServing {
        Objects.requireNonNull(tree, "tree");
    }
}
