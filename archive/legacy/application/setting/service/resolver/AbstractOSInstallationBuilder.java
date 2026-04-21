package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.PartitionRequest;
import com.example.serverprovision.application.setting.model.request.RootPasswordRequest;
import com.example.serverprovision.application.setting.model.request.TimezoneRequest;
import com.example.serverprovision.application.setting.model.request.UserRequest;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;

import java.util.List;

/**
 * 모든 {@link OSInstallationBuilder} 구현체가 공유하는 Request → 도메인 값 객체 변환 유틸을
 * 제공하는 추상 베이스 클래스이다.
 *
 * <p>Linux 계열(RHEL, Ubuntu) 은 파티션·사용자·RootPassword·Timezone 네 가지 값 객체를
 * Request DTO 로부터 동일한 규칙으로 구성한다. 이 공통 로직을 상속 체인 최상단에 배치하여
 * 중복을 제거한다. Windows 계열 등 Linux 가 아닌 OS 를 추가할 때는 이 클래스를 상속하지
 * 않고 {@link OSInstallationBuilder} 를 직접 구현하거나, Linux-특화 메서드만 호출하지 않으면 된다.</p>
 */
public abstract class AbstractOSInstallationBuilder implements OSInstallationBuilder {

    /**
     * {@link PartitionRequest} 목록을 도메인 {@link Partition} 목록으로 변환한다.
     *
     * <p>입력 단위(MB/GB/TB) 를 Kickstart {@code --size} 의 단위인 MiB 로 변환하는 것이
     * 이 변환의 핵심 책임이다.</p>
     */
    protected static List<Partition> buildPartitions(List<PartitionRequest> reqs) {
        return reqs.stream()
                .map(p -> Partition.builder()
                        .mountPoint(p.getMountPoint())
                        .fileSystem(p.getFileSystem())
                        .diskName(p.getDiskName())
                        .sizeInMB(p.getSizeUnit().toMB(p.getSize()))
                        .isGrow(p.isGrow())
                        .build())
                .toList();
    }

    /**
     * {@link UserRequest} 목록을 도메인 {@link User} 목록으로 변환한다.
     * {@code null} 입력은 빈 리스트로 처리된다.
     */
    protected static List<User> buildUsers(List<UserRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(u -> User.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
                        .isSudoer(u.getIsSudoer())
                        .isPasswordEncrypted(u.isPasswordEncrypted())
                        .build())
                .toList();
    }

    /**
     * {@link RootPasswordRequest} 를 도메인 {@link RootPassword} 로 변환한다.
     * {@code null} 입력은 그대로 {@code null} 로 반환된다 (root 잠금 상태).
     */
    protected static RootPassword buildRootPassword(RootPasswordRequest req) {
        if (req == null) return null;
        return RootPassword.builder()
                .password(req.getPassword())
                .isPasswordEncrypted(req.isPasswordEncrypted())
                .build();
    }

    /**
     * {@link TimezoneRequest} 를 도메인 {@link Timezone} 으로 변환한다.
     */
    protected static Timezone buildTimezone(TimezoneRequest req) {
        return Timezone.builder()
                .timezone(req.getTimezone())
                .isUTC(req.isUTC())
                .build();
    }
}
