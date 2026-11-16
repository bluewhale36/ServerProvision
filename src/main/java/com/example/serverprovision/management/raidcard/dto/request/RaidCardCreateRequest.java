package com.example.serverprovision.management.raidcard.dto.request;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 신규 RAID 카드 등록 요청. (vendor, modelName) 조합은 살아 있는(비삭제 · 비 Deprecated) 레코드
 * 안에서 유일해야 한다(MA7 D7).
 *
 * <p>{@code pciSubsystemId} 는 선택 입력 — 비우면 '미확인' 이며, 값이 있으면 서비스가
 * {@code PciSubsystemId.parse} 로 VO 화한다. 형식 검증은 여기서 1차(@Pattern), 정규화는 VO 가 한다.</p>
 */
public record RaidCardCreateRequest(

		@NotNull(message = "제조사를 선택해야 합니다.")
		RaidCardVendor vendor,

		@NotBlank(message = "모델명을 입력해야 합니다.")
		@Size(max = 128, message = "모델명은 128자 이하로 입력해야 합니다.")
		String modelName,

		@NotNull(message = "칩 계열을 선택해야 합니다.")
		RaidChipFamily chipFamily,

		@NotEmpty(message = "지원 RAID 레벨을 1개 이상 선택해야 합니다.")
		List<RaidLevel> supportedRaidLevels,

		@NotNull(message = "캐시 용량을 입력해야 합니다. 캐시가 없는 모델은 0 을 입력합니다.")
		@Min(value = 0, message = "캐시 용량은 0 이상이어야 합니다.")
		@Max(value = 1024, message = "캐시 용량은 1024GB 이하로 입력해야 합니다.")
		Integer cacheCapacityGb,

		@Pattern(regexp = "^$|^\\[?(0[xX])?[0-9a-fA-F]{1,4}:(0[xX])?[0-9a-fA-F]{1,4}]?$",
				message = "PCI Subsystem ID 형식이 올바르지 않습니다. 예: 1458:0011")
		String pciSubsystemId,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해야 합니다.")
		String description
) {

	/** 등록 · 수정 요청이 같이 쓰는 안내(애너테이션 값이라 상수). */
	static final String LEVELS_NOT_CREATABLE =
			"고른 칩 계열이 만들 수 없는 RAID 레벨이 선택돼 있습니다. 칩 계열 아래 안내를 확인해야 합니다.";

	/**
	 * 칩 계열이 만들 수 없는 레벨(예: MPT IR 의 RAID5)은 등록 폼이 잠그고(1차 차단) 여기는 direct POST 안전망이다(R17-2).
	 * 계열 · 레벨이 비면 참 — 그 누락은 @NotNull · @NotEmpty 가 알린다.
	 */
	@AssertTrue(message = LEVELS_NOT_CREATABLE)
	public boolean isSupportedRaidLevelsCreatable() {
		return levelsCreatable(chipFamily, supportedRaidLevels);
	}

	static boolean levelsCreatable(RaidChipFamily chipFamily, List<RaidLevel> levels) {
		return chipFamily == null || levels == null || chipFamily.canCreateAll(levels);
	}
}
