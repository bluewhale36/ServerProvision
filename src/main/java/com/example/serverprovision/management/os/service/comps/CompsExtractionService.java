package com.example.serverprovision.management.os.service.comps;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import com.example.serverprovision.management.os.vo.TreeInfo;
import java.nio.file.Path;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;

import com.example.serverprovision.global.job.JobStage;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.UnsupportedExtractionException;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService.PreparedIsoPath;
import com.example.serverprovision.management.os.service.comps.extractor.CompsExtractionResult;
import com.example.serverprovision.management.os.service.comps.extractor.CompsExtractorStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 설치 환경·패키지 그룹 추출 파이프라인.
 * <pre>
 *   PREPARE_ISO → VERIFY_RELEASE → SCAN_REPO → PARSE_COMPS → MERGE_SAVE → (COMPLETED | FAILED)
 * </pre>
 * 각 단계 전이는 {@link BackgroundJobService#report} 로 보고되며, 최종 성공/실패 시 {@code complete}/{@code fail} 이 호출된다.
 * 실제 DB 반영은 {@link CompsMergeService} 에 위임해 {@code @Transactional} 경계를 확실히 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompsExtractionService {

	/**
	 * comps 추출 파이프라인의 5 단계. ordinal 이 chunk 인덱스로 쓰인다.
	 */
	public enum Stage implements JobStage {
		PREPARE_ISO("ISO 이미지 준비"),
		VERIFY_RELEASE("ISO 버전 확인"),
		SCAN_REPO("저장소 탐색"),
		PARSE_COMPS("패키지 그룹 정보 해석"),
		MERGE_SAVE("설치 환경, 패키지 그룹 저장");

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
	private final IsoRegistrationFailureRecorder registrationFailureRecorder;   // E4-1-b-2 D-20

	@Async("compsExtractionExecutor")
	public void extractAsync(OSMetadata osMetadata, ISO iso, String jobId) {
		try {
			backgroundJobService.startStage(jobId, Stage.PREPARE_ISO);

			try (PreparedIsoPath prepared = isoPreparationService.prepare(iso.getIsoPath())) {

				backgroundJobService.startStage(jobId, Stage.VERIFY_RELEASE);
				verifyRelease(osMetadata, iso, prepared.effectivePath());

				backgroundJobService.startStage(jobId, Stage.SCAN_REPO);

				CompsExtractorStrategy strategy = strategies.stream()
						.filter(s -> s.supports(osMetadata.getOsName()))
						.findFirst()
						.orElseThrow(() -> new UnsupportedExtractionException(osMetadata.getOsName()));

				backgroundJobService.startStage(jobId, Stage.PARSE_COMPS);

				CompsExtractionResult result = strategy.extract(prepared.effectivePath());

				backgroundJobService.startStage(jobId, Stage.MERGE_SAVE);

				compsMergeService.mergeAndSave(osMetadata.getId(), iso.getId(), result);
			}

			backgroundJobService.complete(jobId);
			log.info(
					"[CompsExtractionService] 추출 완료. jobId={}, osMetadataId={}, isoId={}",
					jobId, osMetadata.getId(), iso.getId()
			);

		} catch (Exception e) {
			log.error(
					"[CompsExtractionService] 추출 실패. jobId={}, osMetadataId={}, isoId={}, 원인={}",
					jobId, osMetadata.getId(), iso.getId(), e.getMessage(), e
			);
			backgroundJobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		}
	}

	/**
	 * E4-1-b-2 D-20 — ISO 안 트리 설명서의 배포판 · 주 버전이 등록한 OS 와 같은지. 다르거나 트리 설명서가 없으면 ISO 를
	 * '등록 실패' 로 남기고(행 · 파일은 그대로, 운영자가 사유를 보고 지운다) 작업을 실패로 끝낸다. 저장(MERGE_SAVE) 앞이라
	 * 설치 환경이 생기지 않고, 정의서에서는 그 ISO 를 고를 수 없다. 이미 추출이 끝난 ISO 는 [추출] 재실행이 막혀 있어
	 * 기존 환경이 있는 ISO 가 여기서 실패로 바뀌는 경로는 없다. 대상은 Rocky Linux 만(CentOS 는 표기가 여러 가지이고 설치
	 * 지원 대상이 아님). 웹 주소 ISO 는 파일을 열지 않으므로 건너뛴다.
	 */
	private void verifyRelease(OSMetadata osMetadata, ISO iso, String preparedPath) {
		if (osMetadata.getOsName() != OSName.ROCKY_LINUX
				|| preparedPath.startsWith("http://") || preparedPath.startsWith("https://")) {
			return;
		}
		java.util.Optional<TreeInfo> info = TreeInfo.read(Path.of(preparedPath));
		if (info.isEmpty()) {
			fail(iso, "ISO 에서 배포판 정보를 찾지 못했습니다. RHEL 계열 설치 ISO 인지 확인해야 합니다.");
		}
		if (!info.get().matches(osMetadata.getOsName(), osMetadata.getOsVersion())) {
			fail(iso, "ISO 의 실제 버전(" + info.get().label() + ")과 등록한 OS 버전(" + osMetadata.displayName()
					+ ")이 다릅니다. 맞는 OS 아래에 다시 등록해야 합니다.");
		}
	}

	private void fail(ISO iso, String reason) {
		registrationFailureRecorder.record(iso.getId(), reason);
		throw new ExtractionFailedException(reason);
	}
}
