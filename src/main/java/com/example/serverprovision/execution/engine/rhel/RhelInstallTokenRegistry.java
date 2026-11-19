package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.ServingTokenRegistry;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** RHEL 설치(kickstart · 설치 트리)의 일회용 URL 발급소(E4-1-b-4-2). 발급 · 회수 규칙은 {@link ServingTokenRegistry} 와 같다. */
@Component
public class RhelInstallTokenRegistry extends ServingTokenRegistry<RhelServing> {

    static final String KICKSTART_FILE = "ks.cfg";
    static final String TREE_PATH = "tree";

    public RhelInstallTokenRegistry(@Value("${pxe.server.base-url:}") String baseUrl) {
        super(baseUrl);
    }

    @Override
    public GuestCredential.ServingKind kind() {
        return GuestCredential.ServingKind.RHEL;
    }

    @Override
    protected String logTag() {
        return "rhelinstall";
    }

    /** 설치 트리 URL — kickstart 의 {@code url} 과 커널 인자 {@code inst.repo} 가 같은 값을 쓴다. */
    public String treeUrlOf(UUID token) {
        return urlOf(token) + "/" + TREE_PATH;
    }

    public String kickstartUrlOf(UUID token) {
        return urlOf(token) + "/" + KICKSTART_FILE;
    }
}
