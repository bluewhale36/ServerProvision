package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartDocument;
import com.example.serverprovision.execution.installtree.vo.InstallTree;

import java.util.Objects;

/**
 * RHEL 서빙 토큰 하나가 가리키는 내용(E4-1-b-4-2). kickstart 는 토큰을 발급할 때 한 번 렌더해 여기 둔다 — 설치 프로그램이 몇 번을
 * 요청해도 같은 내용이 나간다(D-6). 트리는 이 토큰의 {@code /tree/**} 요청을 파일에 대응시킬 때 쓴다.
 */
public record RhelServing(KickstartDocument kickstart, InstallTree tree, RhelInstallMedia media, InstallDisk disk) {

    public RhelServing {
        Objects.requireNonNull(kickstart, "kickstart");
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(media, "media");
        Objects.requireNonNull(disk, "disk");
    }
}
