package com.example.serverprovision.maintenance.os.service;

import com.example.serverprovision.maintenance.os.entity.ISO;
import com.example.serverprovision.maintenance.os.entity.OSEnvironment;
import com.example.serverprovision.maintenance.os.entity.OSImage;
import com.example.serverprovision.maintenance.os.entity.OSPackageGroup;
import com.example.serverprovision.maintenance.os.exception.ISONotFoundException;
import com.example.serverprovision.maintenance.os.exception.OSImageNotFoundException;
import com.example.serverprovision.maintenance.os.repository.ISORepository;
import com.example.serverprovision.maintenance.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.maintenance.os.repository.OSImageRepository;
import com.example.serverprovision.maintenance.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.maintenance.os.service.extractor.CompsExtractionResult;
import com.example.serverprovision.maintenance.os.service.extractor.CompsExtractionResult.EnvironmentData;
import com.example.serverprovision.maintenance.os.service.extractor.CompsExtractionResult.GroupData;
import com.example.serverprovision.maintenance.os.vo.EnvironmentCode;
import com.example.serverprovision.maintenance.os.vo.PackageGroupCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추출 결과를 DB 에 병합 저장하는 전용 트랜잭션 서비스.
 * {@link CompsExtractionService} 의 {@code @Async} 파이프라인과 분리된 이유는 self-invocation 에서는
 * Spring 의 {@code @Transactional} 프록시가 적용되지 않기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompsMergeService {

    private final OSImageRepository osImageRepository;
    private final ISORepository isoRepository;
    private final OSEnvironmentRepository envRepository;
    private final OSPackageGroupRepository grpRepository;

    /**
     * 환경·그룹 upsert → 환경↔그룹 N:M 재구성 → ISO 제공 관계 재구성 순으로 반영한다.
     * 이전과 동일한 관계가 유지되는 경우 DELETE+INSERT 가 발생하지 않도록 Set 비교로 변경점만 교체한다.
     */
    @Transactional
    public void mergeAndSave(Long osImageId, Long isoId, CompsExtractionResult result) {
        OSImage osImage = osImageRepository.findById(osImageId)
                .orElseThrow(() -> new OSImageNotFoundException(osImageId));
        ISO iso = isoRepository.findById(isoId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));

        // 1) 환경 upsert (추출 순서 보존)
        List<OSEnvironment> savedEnvs = new ArrayList<>();
        for (EnvironmentData d : result.environments()) {
            OSEnvironment env = envRepository
                    .findByOsImage_IdAndEnvironmentCode_Value(osImageId, d.environmentCode())
                    .orElseGet(() -> envRepository.save(OSEnvironment.builder()
                            .osImage(osImage)
                            .environmentCode(EnvironmentCode.of(d.environmentCode()))
                            .displayName(d.displayName())
                            .description(d.description())
                            .isDefault(d.isDefault())
                            .build()));
            env.update(d.displayName(), d.description(), d.isDefault());
            savedEnvs.add(env);
        }

        // 2) 그룹 upsert (추출 순서 보존). 코드 → 엔티티 맵도 3) 에서 재사용.
        Map<String, OSPackageGroup> savedGroupsByCode = new LinkedHashMap<>();
        for (GroupData g : result.allGroups().values()) {
            OSPackageGroup grp = grpRepository
                    .findByOsImage_IdAndGroupCode_Value(osImageId, g.groupCode())
                    .orElseGet(() -> grpRepository.save(OSPackageGroup.builder()
                            .osImage(osImage)
                            .groupCode(PackageGroupCode.of(g.groupCode()))
                            .displayName(g.displayName())
                            .description(g.description())
                            .isDefault(g.isDefault())
                            .build()));
            grp.update(g.displayName(), g.description(), g.isDefault());
            savedGroupsByCode.put(g.groupCode(), grp);
        }

        // 3) 환경↔그룹 관계 — 이번 추출에 등장한 환경만 대상. 다른 ISO 제공 환경의 관계는 건드리지 않는다.
        for (int i = 0; i < savedEnvs.size(); i++) {
            OSEnvironment env = savedEnvs.get(i);
            EnvironmentData d = result.environments().get(i);

            List<OSPackageGroup> newGroups = d.groupCodes().stream()
                    .map(savedGroupsByCode::get)
                    .filter(Objects::nonNull)
                    .toList();

            Set<Long> newIds = newGroups.stream().map(OSPackageGroup::getId).collect(Collectors.toSet());
            Set<Long> oldIds = env.getGroups().stream().map(OSPackageGroup::getId).collect(Collectors.toSet());
            if (!newIds.equals(oldIds)) {
                env.replaceGroups(newGroups);
            }
        }

        // 4) ISO 제공 관계 — 이번에 ISO 가 "담고 있다고 밝힌" 환경/그룹 전체로 교체.
        List<OSPackageGroup> allProvided = new ArrayList<>(savedGroupsByCode.values());
        Set<Long> newEnvIds = savedEnvs.stream().map(OSEnvironment::getId).collect(Collectors.toSet());
        Set<Long> newGrpIds = allProvided.stream().map(OSPackageGroup::getId).collect(Collectors.toSet());
        Set<Long> oldEnvIds = iso.getProvidedEnvironments().stream().map(OSEnvironment::getId).collect(Collectors.toSet());
        Set<Long> oldGrpIds = iso.getProvidedPackageGroups().stream().map(OSPackageGroup::getId).collect(Collectors.toSet());

        if (!newEnvIds.equals(oldEnvIds) || !newGrpIds.equals(oldGrpIds)) {
            iso.replaceProvisions(savedEnvs, allProvided);
        }

        // 모든 merge 가 정상 종료된 시점에만 완료 타임스탬프를 찍는다.
        // 이 줄에 도달하지 못하고 예외로 중단되면 extractedAt 은 null 로 유지되어 재추출이 가능하다.
        iso.markExtracted();

        log.info("[CompsMergeService] 병합 완료. osImageId={}, isoId={}, envs={}, groups={}",
                osImageId, isoId, savedEnvs.size(), allProvided.size());
    }
}
