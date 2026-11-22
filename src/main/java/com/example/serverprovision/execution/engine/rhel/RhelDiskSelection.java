package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.OsVolume;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;

import java.util.Optional;

/**
 * RHEL 설치 대상 디스크 판정(E4-1-b-4-2, b-6 을 합침). kickstart 는 디스크를 {@code /dev/disk/by-id} 이름으로 지목하므로
 * OS 영역 RAID 볼륨의 WWN 에 {@code wwn-0x} 를 붙인다. 변환은 Windows 확증과 같은 {@code RaidChipFamily.volumeWwnOf} 다.
 * 이름이 실제 udev 이름과 어긋나도 다른 디스크를 지우지 않는다: kickstart 의 {@code ignoredisk --only-use} 가 없는 디스크를
 * 가리키면 설치 프로그램이 거기서 멈춘다. 이름의 실측 확인은 CP7 V-8 이다.
 */
public final class RhelDiskSelection {

    static final String BY_ID_PREFIX = "wwn-0x";

    public sealed interface Selection {

        /** 판정 성립 — 렌더에 쓸 디스크. */
        record Confident(InstallDisk disk) implements Selection {
        }

        /** RAID 구성 뒤에 정해진다 — 준비도를 막지 않지만 서빙은 기다린다. */
        record Deferred(String note) implements Selection {
        }

        /** 정할 수 없다 — 준비도 BLOCKED. */
        record Blocked(String wire, String note) implements Selection {
        }
    }

    private RhelDiskSelection() {
    }

    public static Selection judge(OsVolume located) {
        return switch (located) {
            case OsVolume.Deferred deferred -> new Selection.Deferred(deferred.note());
            case OsVolume.Blocked blocked -> new Selection.Blocked(blocked.wire(), blocked.note());
            case OsVolume.Materialized materialized -> judgeMaterialized(materialized);
        };
    }

    private static Selection judgeMaterialized(OsVolume.Materialized materialized) {
        Optional<OsVolume.Blocked> unusable = materialized.unusable();
        if (unusable.isPresent()) {
            return new Selection.Blocked(unusable.get().wire(), unusable.get().note());
        }
        String wwn = materialized.osVolumeWwn();
        if (wwn == null) {
            return new Selection.Blocked("os volume wwn missing",
                    "OS 볼륨의 WWN 을 알 수 없습니다. RAID 카드 정보를 다시 모아야 합니다.");
        }
        return new Selection.Confident(new InstallDisk(BY_ID_PREFIX + wwn));
    }
}
