package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.OSTemplate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RockyLinuxInstallation.class, name = "ROCKY_LINUX")
})
public abstract class OSInstallation extends OSTemplate {

    protected OSInstallation(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }

    /**
     * 주어진 런타임 컨텍스트를 바탕으로 완성된 Kickstart 스크립트 문자열을 반환한다.
     *
     * @param ctx 호스트명·IP·설치 소스 URL 등 네트워크 의존 정보를 담은 컨텍스트
     * @return 완성된 Kickstart 파일 내용 (text/plain)
     */
    public abstract String getKickstartScript(KickstartContext ctx);
}
