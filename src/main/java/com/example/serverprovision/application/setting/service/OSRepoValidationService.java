package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.dto.ValidationWarning;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.domain.os.model.setting.RHELBasedSetting;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import com.example.serverprovision.domain.os.repository.OSPackageRefRepository;
import com.example.serverprovision.domain.os.repository.OSServiceRefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 이미 resolver 를 거친 {@link AbstractSettingProcess} 목록을 OS 저장소 인덱스와 대조하여
 * 미확인 패키지/서비스 이름을 {@link ValidationWarning} 목록으로 반환하는 서비스.
 *
 * <p>검증 정책:
 * <ul>
 *     <li>대상은 {@link OSSetting} + {@link RHELBasedSetting} 조합만. (Ubuntu/Windows 는 추후 확장)</li>
 *     <li>OSMetadata 의 인덱스 갯수가 0 이면 "아직 인덱싱 안됨" 으로 해석하여 검증을 건너뛴다.
 *         관리자가 아직 인덱싱하지 않은 OS 에 대해 무차별 경고를 쏟지 않기 위함.</li>
 *     <li>검증 결과는 경고만 반환하며 예외를 던지지 않는다. 본 저장은 차단되지 않는다.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSRepoValidationService {

    private final OSPackageRefRepository packageRefRepository;
    private final OSServiceRefRepository serviceRefRepository;

    /**
     * 해석된 프로세스 목록을 순회하며 OS 저장소 인덱스에 없는 이름을 경고로 수집한다.
     *
     * @param processes resolver dispatch 가 완료된 도메인 모델 목록
     * @return 비차단 경고 목록 (빈 목록 가능)
     */
    @Transactional(readOnly = true)
    public List<ValidationWarning> validate(List<AbstractSettingProcess> processes) {
        if (processes == null || processes.isEmpty()) return List.of();

        List<ValidationWarning> warnings = new ArrayList<>();
        for (int i = 0; i < processes.size(); i++) {
            AbstractSettingProcess p = processes.get(i);
            if (!(p instanceof OSSetting os)) continue;
            if (!(os.getOsSetting() instanceof RHELBasedSetting rhel)) continue;

            Long metaId = os.getOsMetadata().id();
            validateSingle(i, metaId, rhel, warnings);
        }
        return warnings;
    }

    private void validateSingle(int processIndex, Long metaId,
                                RHELBasedSetting rhel,
                                List<ValidationWarning> warnings) {

        // 패키지 검증 — 인덱스 갯수 0 이면 건너뜀.
        List<String> pkgs = rhel.getAdditionalPackages();
        if (!pkgs.isEmpty() && packageRefRepository.countByOsMetadata_Id(metaId) > 0) {
            Set<String> existing = lookupExisting(
                    packageRefRepository.findExistingNames(metaId, pkgs));
            for (int j = 0; j < pkgs.size(); j++) {
                String name = pkgs.get(j);
                if (name == null || name.isBlank()) continue;
                if (!existing.contains(name)) {
                    warnings.add(new ValidationWarning(
                            "processList[" + processIndex + "].additionalPackages[" + j + "]",
                            name,
                            "이 OS 의 기본 저장소 인덱스에서 확인되지 않은 패키지입니다. "
                                    + "EPEL/사내 저장소에서 제공되면 정상 설치될 수 있으니 이름을 확인하고 진행하세요."
                    ));
                }
            }
        } else if (!pkgs.isEmpty()) {
            log.debug("[OSRepoValidationService] 패키지 인덱스 미존재로 검증 건너뜀. osMetadataId={}", metaId);
        }

        // 서비스 검증 — 인덱스 갯수 0 이면 건너뜀.
        List<ServiceDirective> services = rhel.getServices();
        if (!services.isEmpty() && serviceRefRepository.countByOsMetadata_Id(metaId) > 0) {
            List<String> names = services.stream().map(ServiceDirective::name).toList();
            Set<String> existing = lookupExisting(
                    serviceRefRepository.findExistingNames(metaId, names));
            for (int j = 0; j < services.size(); j++) {
                String name = services.get(j).name();
                if (name == null || name.isBlank()) continue;
                if (!existing.contains(name)) {
                    warnings.add(new ValidationWarning(
                            "processList[" + processIndex + "].services[" + j + "].name",
                            name,
                            "이 OS 기본 저장소 패키지가 제공하는 systemd unit 에서 확인되지 않습니다. "
                                    + "이름을 다시 확인하거나 별도로 unit 파일을 배포하는지 확인하세요."
                    ));
                }
            }
        } else if (!services.isEmpty()) {
            log.debug("[OSRepoValidationService] 서비스 인덱스 미존재로 검증 건너뜀. osMetadataId={}", metaId);
        }
    }

    private Set<String> lookupExisting(Collection<String> existing) {
        return existing == null ? Collections.emptySet() : Set.copyOf(existing);
    }
}
