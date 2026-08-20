package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.service.reference.os.DiskGroupRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAID_CONFIGURATION — RAID 카드 소프트참조와 디스크 묶음 규칙 검사(U4-1-1 v2).
 *
 * <p>카드는 ISO 와 같은 관례로 본다: 실존(삭제 포함) 404 · disabled 409(field=raidCardId) · deprecated 는 저장을
 * 막지 않고 상세에 경고. 값 규칙은 {@link DiskGroupRules} 가 <b>그 카드</b>를 재료로 판정한다 — 만들 수 있는 레벨과
 * 최소 디스크 수가 카드에 달려 있기 때문이다. 카드를 전제하지 않는 정의서(RAID 없음 묶음만)는 저장소를 부르지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class RaidConfigurationReferenceInspector implements ProcessReferenceInspector {

    private final RaidCardRepository raidCardRepository;

    @Override
    public SettingProcessType target() {
        return SettingProcessType.RAID_CONFIGURATION;
    }

    @Override
    public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
        RaidConfigurationRequest request = (RaidConfigurationRequest) process;
        DiskGroupRules.validate(request.getDiskGroups(), resolveRaidCard(request.getRaidCardId()));
    }

    /** 카드를 전제하지 않으면 {@code null} — 저장소를 부르지 않는다. */
    private RaidCard resolveRaidCard(Long raidCardId) {
        if (raidCardId == null) {
            return null;
        }
        RaidCard card = raidCardRepository.findByIdAndIsDeletedFalse(raidCardId)
                .orElseThrow(() -> new RaidCardNotFoundException(raidCardId));
        if (!card.isEnabled()) {
            throw new DisabledResourceReferenceException("raidCardId", "RAID 카드 " + card.displayName());
        }
        return card;
    }

    @Override
    public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
        RaidConfigurationRequest request = (RaidConfigurationRequest) process;
        List<String> names = new ArrayList<>();
        if (request.getRaidCardId() != null) {
            raidCardRepository.findByIdAndIsDeletedFalse(request.getRaidCardId())
                    .filter(LifecycleEntity::isDeprecated)
                    .ifPresent(card -> names.add("RAID 카드 " + card.displayName()));
        }
        return names;
    }

    /** MK4-2 드리프트용 파일 자원 목록 — RAID 카드는 파일 없는 메타 자원이라 담지 않는다. */
    @Override
    public List<ResourceKey> referencedResources(AbstractProcessRequest process) {
        return List.of();
    }
}
