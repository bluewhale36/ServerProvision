package com.example.serverprovision.management.raidcard.enums;


/**
 * RAID 카드 칩 계열(E3.5-1) — 제어 계열(드라이버 · CLI · 파서)을 정하는 축이다(사전 조사 §2).
 * 브랜드가 아니라 PCI Vendor:Device 가 판별자다: {@code 1000:0097} = SAS3008(IR) · {@code 1000:005d} = SAS3108(MegaRAID).
 */
public enum RaidChipFamily {

    /** Fusion-MPT SAS-3 의 Integrated RAID 펌웨어 — sas3ircu 계열 (예: GIGABYTE CRA3338). */
    MPT_IR("MPT IR (sas3ircu 계열)") {
        @Override
        public java.util.List<String> chipPciIds() {
            return java.util.List.of("1000:0097");   // SAS3008 IR
        }

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
    MEGARAID("MegaRAID (storcli 계열)") {
        @Override
        public java.util.List<String> chipPciIds() {
            return java.util.List.of("1000:005d");   // SAS3108 MegaRAID
        }

        @Override
        public int maxVolumes() {
            return 64;   // VD 상한(사전 조사 §2)
        }

        @Override
        public String memberCountBlockReason(RaidLevel level, int memberCount) {
            return null;   // 레벨별 수량 제약은 실측 표본이 생기면 그때 채운다
        }
    };

    private final String displayName;

    RaidChipFamily(String displayName) {
        this.displayName = displayName;
    }

    /** 카드 등록 화면 · 거절 문구의 계열 표기. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 이 계열로 판별되는 칩의 PCI Vendor:Device 목록 — 칩 판별의 SSOT(E3.5-3 CP4 검수 반영).
     * 새 칩(같은 계열의 다른 컨트롤러)은 여기에만 추가한다 — 파서와 에이전트 힌트가 함께 따라온다.
     */
    public abstract java.util.List<String> chipPciIds();

    /**
     * 에이전트 동봉 판별 힌트 — {@code "1000:0097=MPT_IR 1000:005d=MEGARAID"} 형태(공백 구분).
     * agent.sh 는 이 맵으로만 계열을 판별하므로 스크립트에 칩 id 가 남지 않는다.
     */
    public static String agentChipHint() {
        return java.util.Arrays.stream(values())
                .flatMap(family -> family.chipPciIds().stream().map(id -> id + "=" + family.name()))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** lspci 원문에서 계열 판별 — 파서와 에이전트가 같은 id 집합 · 같은 선언 순서로 판별한다. */
    public static java.util.Optional<RaidChipFamily> fromLspci(String lspci) {
        for (RaidChipFamily family : values()) {
            for (String id : family.chipPciIds()) {
                if (lspci.contains("[" + id + "]")) {
                    return java.util.Optional.of(family);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** 이 계열이 만들 수 있는 볼륨 수 상한 — 계획 검증(E3.5-2)이 정의서 검증이 못 잡는 수량 층을 여기서 잡는다. */
    public abstract int maxVolumes();

    /**
     * VD 파라미터(E3.5-6 — CONFIGURE VIRTUAL DRIVE PARAMETERS 8축) 지원 여부. 폼 잠금과 서버 가드가
     * 이 판정 하나를 함께 본다(SSOT). 값 집합이 계열마다 갈라지는 카드가 오면 이 boolean 을
     * "지원 값 집합" 질의(supportedWritePolicies() 류)로 넓힌다 — 그때까지 미리 만들지 않는다(plan D1).
     */
    public boolean supportsVdParameters() {
        return this == MEGARAID;
    }

    /**
     * 레벨 × 멤버 수의 계열 제약 — {@code null} = 통과, 문자열 = 거절 사유
     * ({@code SupportedRaidLevels.blockReasonFor} 반환 규약).
     */
    public abstract String memberCountBlockReason(RaidLevel level, int memberCount);
}
