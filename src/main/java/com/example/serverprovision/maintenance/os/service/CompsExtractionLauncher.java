package com.example.serverprovision.maintenance.os.service;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.maintenance.os.entity.ISO;
import com.example.serverprovision.maintenance.os.entity.OSImage;
import com.example.serverprovision.maintenance.os.exception.AlreadyExtractedException;
import com.example.serverprovision.maintenance.os.exception.ISONotFoundException;
import com.example.serverprovision.maintenance.os.exception.OSImageNotFoundException;
import com.example.serverprovision.maintenance.os.repository.ISORepository;
import com.example.serverprovision.maintenance.os.repository.OSImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * comps 추출 시작 조정자.
 * <ul>
 *   <li>OS/ISO 엔티티 조회 후 {@link BackgroundJobService} 에 Job 등록</li>
 *   <li>같은 {@code isoPath} 로 실행 중인 활성 추출 Job 이 있으면 새로 만들지 않고 기존 jobId 반환</li>
 *   <li>등록 후 {@link CompsExtractionService#extractAsync} 를 트리거해 비동기 실행 개시</li>
 * </ul>
 * Stage S1 전환으로 기존 {@code ExtractionTaskService} 의 조정자 역할을 대체한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompsExtractionLauncher {

    private final OSImageRepository osImageRepository;
    private final ISORepository isoRepository;
    private final BackgroundJobService backgroundJobService;
    private final CompsExtractionService compsExtractionService;

    public String startExtraction(Long osImageId, Long isoId) {
        OSImage osImage = osImageRepository.findByIdAndIsDeletedFalse(osImageId)
                .orElseThrow(() -> new OSImageNotFoundException(osImageId));
        ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));

        String isoPath = iso.getIsoPath();

        // 동일 ISO 에 대해 이미 활성 추출 Job 이 있다면 새로 만들지 않고 그 jobId 를 재사용한다.
        String existingJobId = findActiveExtractionJob(isoPath);
        if (existingJobId != null) {
            log.info("[CompsExtractionLauncher] 기존 추출 Job 재사용. isoId={}, jobId={}", isoId, existingJobId);
            return existingJobId;
        }

        // 이미 추출이 완료된 ISO(extractedAt 이 찍힌 경우) 에 대해서는 재추출을 막는다.
        // 중단된 추출(일부만 저장되고 예외로 끝난 경우) 은 extractedAt 이 null 이므로 재추출 허용.
        if (iso.isExtractionComplete()) {
            log.info("[CompsExtractionLauncher] 이미 추출 완료된 ISO — 재추출 차단. isoId={}, extractedAt={}",
                    isoId, iso.getExtractedAt());
            throw new AlreadyExtractedException(isoId);
        }

        String title = osImage.getOsName().getDisplayName() + " " + osImage.getOsVersion() + " — 환경·패키지 그룹 추출";
        String jobId = backgroundJobService.register(JobType.COMPS_EXTRACTION, title, isoPath);
        compsExtractionService.extractAsync(osImage, iso, jobId);
        log.info("[CompsExtractionLauncher] 추출 Job 시작. jobId={}, osImageId={}, isoId={}",
                jobId, osImageId, isoId);
        return jobId;
    }

    private String findActiveExtractionJob(String isoPath) {
        for (BackgroundJob job : backgroundJobService.snapshot()) {
            if (job.getType() == JobType.COMPS_EXTRACTION
                    && job.getStatus().isActive()
                    && isoPath.equals(job.getSubtitle())) {
                return job.getId();
            }
        }
        return null;
    }
}
