package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 결론 없이 끝난 중복 확인 창(취소 · 만료)의 업로드 트리를 지운다(HF30-3).
 *
 * <p>BIOS · BMC · 드라이버의 취소 정리가 글자 그대로 같았고, 만료 회수는 정리를 아예 하지 않아 트리가 남았다(HF30-2 D-2).
 * 무엇을 지울지는 payload 가 정한다({@link NudgePayload#uploadedTreeToDiscard()}).</p>
 *
 * <p>트리 뿌리에 마커가 있으면 지우지 않는다. 창의 업로드 트리는 마커 없이 대기하고 등록이 끝날 때 마커가 생기므로, 마커가 있다는 것은
 * 그 사이 누군가 이 폴더를 자원으로 등록했다는 뜻이다(예: 만료 뒤 회수 전에 기존 폴더 등록).</p>
 *
 * <p>IN_TREE 만 보는 근거(HF30-3 CP4 확인): 업로드 뒤 창의 payload 는 BIOS · BMC · 드라이버 등록 서비스 세 곳만 만들고, 셋 모두
 * tempFilePath 에 폴더를 넣는다(펌웨어는 파일의 상위 폴더, 드라이버는 대상 폴더이며 단일 파일 업로드도 그 폴더에 푼다). 세 자원의
 * 마커 배치는 모두 IN_TREE 다. 파일 하나를 담는 payload 가 생기면 배치를 payload 가 정하게 바꾼다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NudgeUploadDiscarder {

	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final ProvisionMarkerService provisionMarkerService;

	public void discard(NudgeSession session, String warnContext) {
		session.payload().uploadedTreeToDiscard().ifPresent(tree -> discardTree(session, tree, warnContext));
	}

	private void discardTree(NudgeSession session, Path tree, String warnContext) {
		if (Files.exists(provisionMarkerService.resolveMarkerFile(tree, MarkerLayout.IN_TREE))) {
			log.info("[{}] 업로드 트리에 마커가 있어 남김(이미 자원으로 등록). nudgeId={} path={}", warnContext, session.nudgeId(), tree);
			return;
		}
		bundleTreeCleanupService.purgeExistingTree(tree, warnContext);
		log.info("[{}] 업로드 트리 정리. nudgeId={} path={}", warnContext, session.nudgeId(), tree);
	}
}
