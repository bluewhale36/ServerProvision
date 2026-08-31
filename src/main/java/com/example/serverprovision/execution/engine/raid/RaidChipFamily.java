package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;

/**
 * RAID 카드 칩 계열(E3.5-1) — 제어 계열(드라이버 · CLI · 파서)을 정하는 축이다(사전 조사 §2).
 * 브랜드가 아니라 PCI Vendor:Device 가 판별자다: {@code 1000:0097} = SAS3008(IR) · {@code 1000:005d} = SAS3108(MegaRAID).
 */
public enum RaidChipFamily {

    /** Fusion-MPT SAS-3 의 Integrated RAID 펌웨어 — sas3ircu 계열 (예: GIGABYTE CRA3338). */
    MPT_IR {
        @Override
        public int maxVolumes() {
            return 2;   // IR 펌웨어의 볼륨 상한(사전 조사 §2)
        }

        @Override
        public String memberCountBlockReason(RaidLevel level, int memberCount) {
            return switch (level) {
                case RAID1 -> memberCount == 2 ? null
                        : "MPT_IR 의 RAID1 은 정확히 2대로만 구성됩니다 — " + memberCount + "대는 만들 수 없습니다";
                case RAID10 -> memberCount >= 3 && memberCount <= 10 ? null
                        : "MPT_IR 의 RAID10 은 3~10대 구성입니다 — " + memberCount + "대는 만들 수 없습니다";
                case RAID0 -> memberCount <= 10 ? null
                        : "MPT_IR 의 RAID0 은 최대 10대 구성입니다 — " + memberCount + "대는 만들 수 없습니다";
                default -> null;   // 그 외 레벨은 지원 레벨 검증(정의서 저장 시)이 이미 거른다
            };
        }
    },

    /** MegaRAID RAID-on-Chip — storcli 계열 (예: AVAGO MegaRAID 9361-8i). */
    MEGARAID {
        @Override
        public int maxVolumes() {
            return 64;   // VD 상한(사전 조사 §2)
        }

        @Override
        public String memberCountBlockReason(RaidLevel level, int memberCount) {
            return null;   // 레벨별 수량 제약은 실측 표본이 생기면 그때 채운다
        }
    };

    /** 이 계열이 만들 수 있는 볼륨 수 상한 — 계획 검증(E3.5-2)이 정의서 검증이 못 잡는 수량 층을 여기서 잡는다. */
    public abstract int maxVolumes();

    /**
     * 레벨 × 멤버 수의 계열 제약 — {@code null} = 통과, 문자열 = 거절 사유
     * ({@code SupportedRaidLevels.blockReasonFor} 반환 규약).
     */
    public abstract String memberCountBlockReason(RaidLevel level, int memberCount);
}
