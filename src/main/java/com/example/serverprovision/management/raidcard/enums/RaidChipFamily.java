package com.example.serverprovision.management.raidcard.enums;

import com.example.serverprovision.management.raidcard.vo.DrivesPerSpan;
import com.example.serverprovision.management.raidcard.vo.MemberCountRule;


/**
 * RAID 카드 칩 계열(E3.5-1) — 제어 계열(드라이버 · CLI · 파서)을 정하는 축이다(사전 조사 §2).
 * 판별자는 칩 ID 목록이 아니라 <b>진단 리눅스 커널이 카드에 붙인 드라이버</b>다(R17) — 칩 → 드라이버 대응은 커널의
 * {@code modules.alias} 가 이미 갖고 있고(실측: {@code 1000:10e2} · {@code 1000:005d} → megaraid_sas, {@code 1000:0097} → mpt3sas),
 * 앱이 그 사본을 들면 HF22 처럼 낡는다. 계열이 소유하는 것은 드라이버 이름 하나다.
 */
public enum RaidChipFamily {

    /** Fusion-MPT SAS-3 의 Integrated RAID 펌웨어 — sas3ircu 계열 (예: GIGABYTE CRA3338). */
    MPT_IR("MPT IR (sas3ircu 계열)") {
        @Override
        public String kernelDriver() {
            return "mpt3sas";   // IR(RAID) · IT(HBA) 를 함께 다룬다 — IR 여부는 에이전트가 sas3ircu 로 확인(NOT_IR)
        }

        @Override
        public int maxVolumes() {
            return 2;   // IR 펌웨어의 볼륨 상한(사전 조사 §2)
        }

        /**
         * IR 펌웨어의 볼륨 대수(GIGABYTE CRA3338 스펙 — RAID 0 · 1E · 10 은 볼륨당 10대, RAID 1 은 2대). RAID10 은 홀수를
         * 허용하는 IR 방식이라 하한이 3 이다. 스팬 개념이 없어 span 은 읽지 않는다. RAID5 · RAID6 은 IR 이 만들지 못해
         * 지원 레벨 검증이 먼저 거르므로 레벨 하한만 둔다.
         */
        @Override
        public MemberCountRule memberCountRule(RaidLevel level, boolean cardHasCache, DrivesPerSpan span) {
            return switch (level) {
                case RAID0 -> MemberCountRule.of(level, level.minimumDisks(cardHasCache), 10);
                case RAID1 -> MemberCountRule.of(level, 2, 2);
                case RAID10 -> MemberCountRule.of(level, 3, 10);
                case RAID5, RAID6 -> MemberCountRule.of(level, level.minimumDisks(cardHasCache), null);
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
        public String kernelDriver() {
            return "megaraid_sas";
        }

        @Override
        public int maxVolumes() {
            return 64;   // VD 상한(사전 조사 §2)
        }

        /**
         * Broadcom MegaRAID Tri-Mode SW UG — 단일 스팬 최대 32대 · RAID1 은 짝수 2~32대 · RAID10 은 스팬 2~8개 × 스팬당
         * 짝수 대(storcli {@code pdperarray}). RAID6 은 문서상 3대부터지만 레벨 하한(4)을 그대로 둔다(R17-1 비목표).
         */
        @Override
        public MemberCountRule memberCountRule(RaidLevel level, boolean cardHasCache, DrivesPerSpan span) {
            return switch (level) {
                case RAID0, RAID5, RAID6 -> MemberCountRule.of(level, level.minimumDisks(cardHasCache), SINGLE_SPAN_MAX);
                case RAID1 -> new MemberCountRule(level, 2, SINGLE_SPAN_MAX, 2, null);
                case RAID10 -> {
                    DrivesPerSpan p = span == null ? DrivesPerSpan.DEFAULT : span;
                    yield new MemberCountRule(level, p.value() * MIN_SPANS, p.value() * MAX_SPANS, p.value(), p);
                }
            };
        }

        /** RAID10 이면 storcli 가 스팬 구성을 받는다 — 지정이 없으면 기본(2). 그 밖 레벨은 스팬이 없다. */
        @Override
        public DrivesPerSpan spanOf(RaidLevel level, DrivesPerSpan requested) {
            if (level != RaidLevel.RAID10) {
                return null;
            }
            return requested == null ? DrivesPerSpan.DEFAULT : requested;
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

    /** MegaRAID 단일 스팬 최대 대수 · RAID10 스팬 수 범위(Broadcom MegaRAID Tri-Mode SW UG). */
    static final int SINGLE_SPAN_MAX = 32;
    static final int MIN_SPANS = 2;
    static final int MAX_SPANS = 8;

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

    /** 이 계열의 카드를 다루는 커널 드라이버(모듈) 이름 — 계열 판별의 SSOT(R17). */
    public abstract String kernelDriver();

    /**
     * 에이전트 동봉 판별 힌트 — {@code "mpt3sas=MPT_IR megaraid_sas=MEGARAID"}(선언 순서 · 공백 구분).
     * agent.sh 는 이 맵으로만 드라이버 → 계열을 정하므로 스크립트에 계열 · 모듈 이름이 남지 않는다.
     */
    public static String agentDriverHint() {
        return java.util.Arrays.stream(values())
                .map(family -> family.kernelDriver() + "=" + family.name())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** 커널 드라이버 이름 → 계열. 계열 밖 드라이버(mpi3mr 등) · null 은 empty. */
    public static java.util.Optional<RaidChipFamily> fromKernelDriver(String driver) {
        return java.util.Arrays.stream(values())
                .filter(family -> family.kernelDriver().equals(driver))
                .findFirst();
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
     * 이 계열로 이 레벨을 몇 대로 만들 수 있는가(R17-1) — 정의서 폼 · 저장 가드 · 계획이 함께 보는 SSOT.
     * span 은 스팬 구성 레벨(MegaRAID RAID10)에서만 읽힌다(null = 기본). 카드 캐시는 레벨 하한(RAID0 단일 디스크)에만 관여한다.
     */
    public abstract MemberCountRule memberCountRule(RaidLevel level, boolean cardHasCache, DrivesPerSpan span);

    /**
     * 볼륨에 실을 스팬 구성 — 이 계열 · 레벨이 스팬을 받지 않으면 null(지정이 있어도 무시하지 않고 저장 가드가 먼저 막는다).
     * 기본 구현 = 스팬 없음.
     */
    public DrivesPerSpan spanOf(RaidLevel level, DrivesPerSpan requested) {
        return null;
    }

    /** 정의서가 이 계열 · 레벨 묶음에 스팬을 지정할 수 있는가 — 폼 노출 · 저장 가드(규칙 10)가 함께 본다. */
    public boolean acceptsSpan(RaidLevel level) {
        return spanOf(level, DrivesPerSpan.DEFAULT) != null;
    }

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
