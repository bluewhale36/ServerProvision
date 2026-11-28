package com.example.serverprovision.execution.installtree.enums;

import com.example.serverprovision.execution.installtree.service.RhelTreeCheck;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.execution.installtree.vo.UbuntuDiskInfo;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 설치 매체를 작업 트리에 어떤 모양으로 꺼내는가. 계열마다 다른 다섯 가지(꺼낼 범위, 필요 공간, 판별, 판별 실패 사유,
 * 디렉터리 등록 허용)를 상수가 맡고, 수명 관리({@code InstallTreeManager})는 계열을 모른다.
 * 새 계열은 상수 하나를 더한다.
 */
public enum InstallMediaLayout {

    /** RHEL 계열. anaconda 가 패키지 저장소를 파일 단위로 읽으므로 ISO 전체를 푼다. */
    RHEL_TREE("images/pxeboot/vmlinuz", "images/pxeboot/initrd.img", TreeFailureReason.NOT_RHEL_TREE) {
        @Override
        public void extract(IsoPreparationService iso, Path isoFile, Path staging) {
            iso.extractInto(isoFile, staging);
        }

        @Override
        public long requiredSpace(long isoBytes) {
            return (long) (isoBytes * WHOLE_HEADROOM);
        }

        @Override
        public Optional<TreeRelease> inspect(Path root) {
            return RhelTreeCheck.inspect(root, kernel(), initrd());
        }

        @Override
        public Optional<TreeFailureReason> directorySourceRefusal() {
            return Optional.empty();   // 마운트해 둔 디렉터리는 풀지 않고 그대로 트리로 쓴다
        }
    },

    /** Ubuntu Server. casper 가 ISO 파일을 통째로 받아 loop 마운트하므로 부팅에 필요한 둘과 판별 파일만 꺼낸다. */
    UBUNTU_LIVE_SERVER("casper/vmlinuz", "casper/initrd", TreeFailureReason.NOT_UBUNTU_SERVER_ISO) {
        @Override
        public void extract(IsoPreparationService iso, Path isoFile, Path staging) {
            iso.extractMembers(isoFile, staging, List.of(kernel(), initrd(), UbuntuDiskInfo.FILE));
        }

        @Override
        public long requiredSpace(long isoBytes) {
            return MEMBER_SPACE;
        }

        @Override
        public Optional<TreeRelease> inspect(Path root) {
            if (!Files.isRegularFile(root.resolve(kernel())) || !Files.isRegularFile(root.resolve(initrd()))) {
                return Optional.empty();
            }
            return UbuntuDiskInfo.read(root).filter(UbuntuDiskInfo::installable).map(UbuntuDiskInfo::release);
        }

        @Override
        public Optional<TreeFailureReason> directorySourceRefusal() {
            return Optional.of(TreeFailureReason.DIRECTORY_ISO_UNSUPPORTED);   // url= 로 내줄 ISO 파일이 없다
        }
    };

    /** 풀린 트리는 ISO 보다 조금 크고 파일 시스템 여유도 남긴다. */
    static final double WHOLE_HEADROOM = 1.2;
    /** 커널과 initrd 둘(실제 ISO 기준 수십 ~ 200 MB)에 여유를 더한 값. */
    static final long MEMBER_SPACE = 512L * 1024 * 1024;

    private final String kernel;
    private final String initrd;
    private final TreeFailureReason notRecognized;

    InstallMediaLayout(String kernel, String initrd, TreeFailureReason notRecognized) {
        this.kernel = kernel;
        this.initrd = initrd;
        this.notRecognized = notRecognized;
    }

    /** ISO 에서 이 모양에 필요한 파일을 staging 에 꺼낸다. 실패는 {@code ExtractionFailedException}. */
    public abstract void extract(IsoPreparationService iso, Path isoFile, Path staging);

    /** 추출 전에 작업 디렉터리에 남아 있어야 할 바이트 수. */
    public abstract long requiredSpace(long isoBytes);

    /** 꺼낸 디렉터리가 이 계열의 설치 매체이면 배포판 · 버전을, 아니면 empty. */
    public abstract Optional<TreeRelease> inspect(Path root);

    /** 디렉터리로 등록된 ISO 를 이 계열에 쓸 수 없으면 그 사유. 쓸 수 있으면 empty. */
    public abstract Optional<TreeFailureReason> directorySourceRefusal();

    /** 설치 커널의 트리 상대경로. */
    public String kernel() {
        return kernel;
    }

    /** 초기 램디스크의 트리 상대경로. */
    public String initrd() {
        return initrd;
    }

    /** 꺼냈지만 이 계열의 매체가 아닐 때의 사유. */
    public TreeFailureReason notRecognized() {
        return notRecognized;
    }
}
