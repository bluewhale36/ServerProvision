package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;

import java.util.List;

/**
 * OS 설치/후처리 단계 폼의 OS 선택지. ({@code GET /provisioning/setting/new} Model)
 *
 * <p>{@code osFamily} 는 2단 판별자 문자열(RHEL_BASED/DEBIAN_BASED/WINDOWS) — 폼 JS 가 계열별 블록 전환과
 * 전송 JSON 의 판별자 구성에 사용한다. Windows 는 E4-1-a-2 에서 실체화됐다.</p>
 *
 * <p>{@code installBlockReason} 은 설치 대상 정책 판정 — null 이면 선택 가능, 문장이 실리면 UI 가 그 옵션을
 * disabled + 이 사유로 표시한다(리눅스 계열 · Windows 설치 소스 미준비). 값의 SSOT 는
 * {@code OsInstallTargetPolicy}(서버 가드와 동일 판정).</p>
 *
 * <p>{@code rootSshSupported} 는 Rocky 10 의 root SSH 허용 칸을 보일지(E4-1-b-2 D-6, 정본 = {@code RhelRelease}).
 * 화면 이름은 {@link #label()} — enum 원문(ROCKY_LINUX)을 화면에 내지 않는다.</p>
 */
public record SettingOSOptionResponse(
        Long osMetadataId,
        String osName,
        String osDisplayName,
        String version,
        OSFamily osFamily,
        String installBlockReason,
        boolean rootSshSupported,
        boolean deprecated,
        String deprecatedAtDisplay,
        String description,
        List<IsoOption> isoList
) {

    /**
     * 설치 ISO 선택지(U2-4) — 사용 가능한(enabled·비삭제) ISO 만 실린다. 이 목록이 비는 OS 는
     * 옵션에서 아예 제외되므로(선택 불가) UI 에 도달하지 않는다. deprecated 메타는 기존
     * 확인 modal·뱃지 관용구용.
     *
     * <p>설치 환경/패키지 그룹은 <b>ISO 스코프</b>다(사용자 확정 2026-07-11 — comps.xml 은 ISO 마다
     * 달라 같은 OS 버전이라도 ISO 별로 제공 목록이 다르다). management 의 ISO 제공 관계
     * (iso_environment/iso_package_group)가 SSOT 이며, 환경의 {@code groupIds} 는
     * "환경 허용 그룹 ∩ 이 ISO 제공 그룹" 으로 서버가 계산해 내린다.</p>
     *
     * <p>{@code path}(등록 경로 전체) · {@code description} · {@code registeredAtDisplay} · {@code integrityStatus} — 정의서의 ISO 상세 열(Miller 2 열)이
     * 보이는 기본 정보(E4-1-b-2 사용자 결정 11-17).</p>
     *
     * <p>{@code blockReason} — 등록 뒤 버전 확인에 실패한 ISO 면 사유(E4-1-b-2 D-20), 아니면 null. ISO 목록(Miller 1 열)이 항목을 잠그고
     * 사유를 보이며, 1단 검사기가 같은 판정으로 거절한다. {@code registrationFailure} 는 그 사유 본문만(항목 안 사유 줄 —
     * 바로 위 "등록 실패" 표지와 겹치는 앞 문장을 빼고 같은 출처에서 쓴다, 7절 "같은 사실은 한 번").</p>
     */
    public record IsoOption(Long id, String name, String path, boolean deprecated, String deprecatedAtDisplay,
                            String description, String registeredAtDisplay, IntegrityStatus integrityStatus,
                            List<EnvironmentOption> environments, List<Option> packageGroups,
                            String blockReason, String registrationFailure) {
    }

    /** 환경/패키지 그룹 선택 항목 (id + 표시명). */
    public record Option(Long id, String name) {
    }

    /**
     * 설치 환경 옵션 — comps.xml 상 환경마다 선택 가능한 패키지 그룹이 다르므로(OSEnvironment
     * @ManyToMany groups), 허용 그룹 id 목록을 함께 실어 폼이 환경 선택 시 그룹을 필터한다.
     */
    public record EnvironmentOption(Long id, String name, List<Long> groupIds, boolean isDefault) {
    }

    /** 화면 이름 — "Rocky Linux 9.6". */
    public String label() {
        return osDisplayName + " " + version;
    }
}
