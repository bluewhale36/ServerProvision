package com.example.serverprovision.execution.engine.ubuntu;

import com.example.serverprovision.execution.engine.osinstall.ServingTokenRegistry;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Ubuntu 설치(커널 · initrd · ISO)의 일회용 URL 발급소. 발급 · 회수 규칙은 {@link ServingTokenRegistry} 와 같다. */
@Component
public class UbuntuInstallTokenRegistry extends ServingTokenRegistry<UbuntuServing> {

    static final String TREE_PATH = "tree";
    /** casper 는 url= 이 .iso 로 끝날 때만 ISO 를 네트워크로 받는다. */
    static final String ISO_FILE = "install.iso";

    public UbuntuInstallTokenRegistry(@Value("${pxe.server.base-url:}") String baseUrl) {
        super(baseUrl);
    }

    @Override
    public GuestCredential.ServingKind kind() {
        return GuestCredential.ServingKind.UBUNTU;
    }

    @Override
    protected String logTag() {
        return "ubuntuinstall";
    }

    /** 작업 트리 URL. 커널은 {@code <이 값>/casper/vmlinuz}, initrd 는 {@code <이 값>/casper/initrd} 다. */
    public String treeUrlOf(UUID token) {
        return urlOf(token) + "/" + TREE_PATH;
    }

    /** 커널 인자 {@code url=} 에 넣을 ISO URL. */
    public String isoUrlOf(UUID token) {
        return urlOf(token) + "/" + ISO_FILE;
    }
}
