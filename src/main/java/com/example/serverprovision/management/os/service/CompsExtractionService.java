package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.job.JobStage;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.exception.UnsupportedExtractionException;
import com.example.serverprovision.management.os.service.IsoPreparationService.PreparedIsoPath;
import com.example.serverprovision.management.os.service.extractor.CompsExtractionResult;
import com.example.serverprovision.management.os.service.extractor.CompsExtractorStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 설치 환경·패키지 그룹 추출 파이프라인.
 * <pre>
 *   PREPARE_ISO → SCAN_REPO → PARSE_COMPS → MERGE_SAVE → (COMPLETED | FAILED)
 * </pre>
 * 각 단계 전이는 {@link BackgroundJobService#report} 로 보고되며, 최종 성공/실패 시 {@code complete}/{@code fail} 이 호출된다.
 * 실제 DB 반영은 {@link CompsMergeService} 에 위임해 {@code @Transactional} 경계를 확실히 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompsExtractionService {

	/**
	 * comps 추출 파이프라인의 4 단계. ordinal 이 chunk 인덱스로 쓰인다.
	 */
	public enum Stage implements JobStage {
		PREPARE_ISO("ISO 이미지 준비"),
		SCAN_REPO("저장소 탐색"),
		PARSE_COMPS("comps.xml 파싱"),
		MERGE_SAVE("환경·패키지 그룹 저장");

		private final String label;

		Stage(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}


	private final List<CompsExtractorStrategy> strategies;
	private final IsoPreparationService isoPreparationService;
	private final CompsMergeService compsMergeService;
	private final BackgroundJobService backgroundJobService;

	@Async("compsExtractionExecutor")
	public void extractAsync(OSImage osImage, ISO iso, String jobId) {
		try {
			backgroundJobService.startStage(jobId, Stage.PREPARE_ISO);

			try (PreparedIsoPath prepared = isoPreparationService.prepare(iso.getIsoPath())) {

				backgroundJobService.startStage(jobId, Stage.SCAN_REPO);

				CompsExtractorStrategy strategy = strategies.stream()
						.filter(s -> s.supports(osImage.getOsName()))
						.findFirst()
						.orElseThrow(() -> new UnsupportedExtractionException(osImage.getOsName()));

				backgroundJobService.startStage(jobId, Stage.PARSE_COMPS);

				CompsExtractionResult result = strategy.extract(prepared.effectivePath());

				backgroundJobService.startStage(jobId, Stage.MERGE_SAVE);

				compsMergeService.mergeAndSave(osImage.getId(), iso.getId(), result);
			}

			backgroundJobService.complete(jobId);
			log.info(
					"[CompsExtractionService] 추출 완료. jobId={}, osImageId={}, isoId={}",
					jobId, osImage.getId(), iso.getId()
			);

		} catch (Exception e) {
			log.error(
					"[CompsExtractionService] 추출 실패. jobId={}, osImageId={}, isoId={}, 원인={}",
					jobId, osImage.getId(), iso.getId(), e.getMessage(), e
			);
			backgroundJobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		}
	}
}
