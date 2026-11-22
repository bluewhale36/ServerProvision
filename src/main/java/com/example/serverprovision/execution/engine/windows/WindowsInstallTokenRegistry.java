package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.osinstall.ServingTokenRegistry;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * WinPE 부팅 번들의 일회용 URL 발급소(E4-1-a-3 D-3). 발급 · 회수 규칙은 {@link ServingTokenRegistry} 가 맡고, 여기서는
 * 번들 파일의 URL 만 조립한다.
 */
@Component
public class WindowsInstallTokenRegistry extends ServingTokenRegistry<WindowsInstallBundle> {

    public WindowsInstallTokenRegistry(@Value("${pxe.server.base-url:}") String baseUrl) {
        super(baseUrl);
    }

    @Override
    public GuestCredential.ServingKind kind() {
        return GuestCredential.ServingKind.WINDOWS;
    }

    @Override
    protected String logTag() {
        return "wininstall";
    }

    public String urlFor(UUID token, WindowsInstallFile file) {
        return issuedUrlOf(token) + "/" + file.fileName();
    }
}
