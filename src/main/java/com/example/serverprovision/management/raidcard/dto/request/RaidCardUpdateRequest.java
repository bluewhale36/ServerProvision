package com.example.serverprovision.management.raidcard.dto.request;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * RAID 카드 수정 요청. 제조사는 수정 불가(BoardModel 선례) — 바꾸려면 삭제 후 재등록.
 */
public record RaidCardUpdateRequest(

		@NotBlank(message = "모델명을 입력하세요.")
		@Size(max = 128, message = "모델명은 128자 이하로 입력해주세요.")
		String modelName,

		@NotEmpty(message = "지원 RAID 레벨을 1개 이상 선택하세요.")
		List<RaidLevel> supportedRaidLevels,

		@NotNull(message = "캐시 용량을 입력하세요. 캐시가 없는 모델은 0 을 입력합니다.")
		@Min(value = 0, message = "캐시 용량은 0 이상이어야 합니다.")
		@Max(value = 1024, message = "캐시 용량은 1024GB 이하로 입력해주세요.")
		Integer cacheCapacityGb,

		@Pattern(regexp = "^$|^\\[?(0[xX])?[0-9a-fA-F]{1,4}:(0[xX])?[0-9a-fA-F]{1,4}]?$",
				message = "PCI Subsystem ID 형식이 올바르지 않습니다. 예: 1458:0011")
		String pciSubsystemId,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description
) {

}
