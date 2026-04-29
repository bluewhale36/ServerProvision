package com.example.serverprovision.domain.os.service;

import com.example.serverprovision.domain.os.entity.OSEnvironment;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageGroup;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.domain.os.service.IsoPreparationService.PreparedIsoPath;
import com.example.serverprovision.domain.os.service.extractor.CompsExtractionResult;
import com.example.serverprovision.domain.os.service.extractor.CompsExtractionResult.EnvironmentData;
import com.example.serverprovision.domain.os.service.extractor.CompsExtractionResult.GroupData;
import com.example.serverprovision.domain.os.service.extractor.CompsExtractorStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompsExtractionService {

    // 추출 결과 요약 — 저장된 환경 수와 패키지 그룹 수
    public record CompsExtractionSummary(int environmentCount, int packageGroupCount) {}

    // 진행률 보고 콜백 — 비동기 실행 시 UI 에 단계별 피드백 전달
    @FunctionalInterface
    public interface ProgressReporter {
        void report(String stage, int progress);
        ProgressReporter NOOP = (stage, progress) -> {};
    }

    private final List<CompsExtractorStrategy> strategies;
    private final IsoPreparationService isoPreparationService;
    private final OSMetadataRepository osMetadataRepository;
    private final OSEnvironmentRepository osEnvironmentRepository;
    private final OSPackageGroupRepository osPackageGroupRepository;

    @Transactional
    public CompsExtractionSummary extractAndSave(Long osMetadataId, ProgressReporter progress) {

        // 1. OS 메타데이터 조회
        progress.report("OS 메타데이터 조회 중", 5);
        OSMetadata meta = osMetadataRepository.findById(osMetadataId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 OS 메타데이터입니다. id=" + osMetadataId));

        // 2. 지원 전략 탐색
        progress.report("OS 타입 지원 확인 중", 10);
        CompsExtractorStrategy strategy = strategies.stream()
                .filter(s -> s.supports(meta.getOsName()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "해당 OS 타입은 자동 추출을 지원하지 않습니다: " + meta.getOsName()));

        // 3. isoMountPath 가 .iso 파일인 경우 마운트 또는 압축 해제 후 유효 경로 확보
        //    PreparedIsoPath 는 AutoCloseable 이므로 finally 에서 정리된다.
        log.info("[CompsExtractionService] 추출 시작. osMetadataId={}, osName={}", osMetadataId, meta.getOsName());
        progress.report("ISO 이미지 준비 중 (마운트/압축 해제)", 15);
        try (PreparedIsoPath prepared = isoPreparationService.prepare(meta.getIsoMountPath())) {

            progress.report("comps.xml 탐색 및 파싱 중", 55);
            CompsExtractionResult result = strategy.extract(prepared.effectivePath());

            // 4. 기존 환경·패키지 그룹 삭제 (OSEnvironment.cascade=REMOVE 로 패키지 그룹도 연쇄 삭제)
            progress.report("기존 환경·패키지 그룹 정리 중", 75);
            List<OSEnvironment> existing = osEnvironmentRepository.findAllByOsMetadata_Id(meta.getId());
            osEnvironmentRepository.deleteAll(existing);
            osEnvironmentRepository.flush(); // 삭제 쿼리를 즉시 DB에 반영해 외래 키 충돌 방지

            // 5. 추출된 환경·패키지 그룹 저장 — 환경 단위로 진행률 갱신
            int pkgCount = 0;
            int envTotal = result.environments().size();
            int envIdx = 0;
            for (EnvironmentData envData : result.environments()) {
                OSEnvironment savedEnv = osEnvironmentRepository.save(
                        OSEnvironment.builder()
                                .osMetadata(meta)
                                .environmentCode(envData.environmentCode())
                                .displayName(envData.displayName())
                                .description(envData.description())
                                .isDefault(envData.isDefault())
                                .build()
                );

                for (String groupCode : envData.groupCodes()) {
                    GroupData group = result.allGroups().get(groupCode);
                    if (group == null) continue; // 환경 목록에 있지만 group 정의가 없는 경우 무시

                    osPackageGroupRepository.save(
                            OSPackageGroup.builder()
                                    .osEnvironment(savedEnv)
                                    .groupCode(group.groupCode())
                                    .displayName(group.displayName())
                                    .description(group.description())
                                    .isDefault(group.isDefault())
                                    .build()
                    );
                    pkgCount++;
                }
                envIdx++;
                int pct = envTotal > 0 ? 85 + (int) (((double) envIdx / envTotal) * 14) : 99;
                progress.report("환경 저장 중 (" + envIdx + "/" + envTotal + ")", pct);
            }

            progress.report("완료", 100);
            log.info("[CompsExtractionService] 저장 완료. 환경={}, 패키지 그룹={}", envTotal, pkgCount);
            return new CompsExtractionSummary(envTotal, pkgCount);
        }
    }
}
