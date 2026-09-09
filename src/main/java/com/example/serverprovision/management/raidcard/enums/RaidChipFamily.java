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

        /**
         * IR 볼륨의 SCSI 식별자(실기 3호 F-7 · 3/3 실측) — sas3ircu 가 보이는 {@code Volume wwid}(8바이트)를
         * 바이트 순서를 뒤집어 {@code 600508E0 00000000} 뒤에 붙인 NAA 6 형식. Windows {@code Get-Disk} UniqueId 와
         * Linux {@code lsblk WWN} 이 같은 값을 보인다. 이미 32자 NAA 로 들어오면 그대로 받는다.
         */
        @Override
        public String windowsUniqueIdOf(String volumeWwn) {
            String hex = normalizeHex(volumeWwn);
            if (hex == null) {
                return null;
            }
            if (hex.length() == 32) {
                return hex;
            }
            if (hex.length() != 16) {
                return null;
            }
            StringBuilder reversed = new StringBuilder(16);
            for (int i = 14; i >= 0; i -= 2) {
                reversed.append(hex, i, i + 2);
            }
            return IR_NAA_PREFIX + reversed;
        }

        /**
         * IR 펌웨어는 나중에 만든 볼륨에 낮은 ID 를 주어 sas3ircu 가 그것을 앞에 나열하지만(322 = 두 번째 볼륨 · 323 = 첫
         * 볼륨), OS 는 만든 순서(ID 내림차순)로 디스크를 번호 매긴다 — 실기 3호 3/3(볼륨 2개). 볼륨 3개 이상은 미실측.
         */
        @Override
        public <T> java.util.List<T> windowsDiskOrder(java.util.List<T> volumes, java.util.function.Function<T, String> idOf) {
            return volumes.stream()
                    .sorted(java.util.Comparator.comparingLong((T v) -> numericId(idOf.apply(v))).reversed())
                    .toList();
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

        /** storcli 의 {@code SCSI NAA Id} 가 곧 Windows UniqueId · lsblk WWN 이다(실기 2호 Run 3 · 3호 2/2). */
        @Override
        public String windowsUniqueIdOf(String volumeWwn) {
            return normalizeHex(volumeWwn);
        }

        /** VD 번호 순(storcli 나열 순) = Windows 디스크 번호 순(실기 2호 Run 3 D1). */
        @Override
        public <T> java.util.List<T> windowsDiskOrder(java.util.List<T> volumes, java.util.function.Function<T, String> idOf) {
            return volumes;
        }
    };

    /** IR 볼륨 NAA 6 식별자의 고정 앞부분 — LSI OUI(0508E0) + 예약 8자리(실기 3호 F-7). */
    static final String IR_NAA_PREFIX = "600508e000000000";

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

    /**
     * 카드 CLI 가 보이는 볼륨 WWN 을 OS 가 보는 SCSI 식별자(Windows {@code Get-Disk} UniqueId = Linux {@code lsblk WWN})로
     * 옮긴다(HF15-5 · 실기 3호 F-7). 설치 대상 디스크 매칭과 설치 뒤 확증이 이 한 변환을 공유한다. 미노출 · 형식 불명은 null.
     * 반환은 소문자 hex, {@code 0x} 접두 없음.
     */
    public abstract String windowsUniqueIdOf(String volumeWwn);

    /**
     * 카드 CLI 나열 순서의 볼륨을 OS 가 디스크 번호를 매기는 순서로 재배열한다(HF15-5 · 실기 3호 F-6) — lsblk 재채집이 없을
     * 때의 보조 규칙. {@code idOf} 는 볼륨 ID 문자열 접근자(계열 무관 제네릭 — 이 enum 이 execution 모듈의 볼륨 타입을
     * 알지 않게).
     */
    public abstract <T> java.util.List<T> windowsDiskOrder(java.util.List<T> volumes, java.util.function.Function<T, String> idOf);

    /** hex 식별자 정규화 — 공백 제거 · {@code 0x} 접두 제거 · 소문자. 비어 있으면 null. */
    public static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("0x")) {
            v = v.substring(2);
        }
        return v.isEmpty() ? null : v;
    }

    private static long numericId(String id) {
        try {
            return id == null ? Long.MIN_VALUE : Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;   // 숫자가 아닌 ID 는 맨 뒤 — 정렬을 깨지 않는다
        }
    }
}
