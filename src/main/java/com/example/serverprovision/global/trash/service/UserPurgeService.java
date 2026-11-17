package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableInventory;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.exception.PurgeTargetNotInTrashException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용자가 화면에서 고른 영구 삭제의 단일 진입점(HF31) — 휴지통 화면과 자원 화면(OS · ISO · 메인보드 · BIOS · BMC · 드라이버 ·
 * RAID 카드)이 같은 길을 탄다: 휴지통 자원 조회 → 자원명 확인 → {@link PurgeExecutor}(도메인 purge · 휴지통 실물 정리 · purge_log 감사 기록).
 *
 * <p>종전에는 자원 화면이 도메인 lifecycle 서비스의 자원명 확인 + purge 를 직접 불러, 휴지통 폴더의 실물이 남고 감사 기록이
 * 없었다(docs C-1). 휴지통에 없는 자원(활성 · 이미 영구 삭제)은 자원명 확인 전에 409 로 거절한다 — 확인을 먼저 하면 "자원 없음" 이
 * 자원명 불일치(400)로 보여 사용자가 맞게 입력하고도 틀렸다는 안내를 받는다(HF31 CP5 F-1). 실행기에서 실패하면 원인 예외를
 * 그대로 다시 던진다 — advice 가 상태코드를 정한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPurgeService {

	private final List<MarkableInventory> inventories;
	private final PurgeExecutor purgeExecutor;

	public void purge(ResourceType resourceType, Long resourceId, String typedName) {
		Markable trashed = requireTrashed(resourceType, resourceId);
		TypedNameGuard.verify(trashed, typedName);
		execute(PurgeRequest.forUserDirect(resourceType, resourceId, null, typedName));
	}

	/**
	 * 중복 확인 창의 [기존 영구 삭제 후 등록](HF30-2) — {@link #purge} 와 같은 순서로 휴지통 후보를 영구 삭제하고 감사 기록의
	 * 진입경로를 NUDGE_REPLACE 로 남긴다. 창 후보가 휴지통 행뿐이라 실행기의 '휴지통 자원만' 전제와 맞는다.
	 * 자원명이 비어 있으면 확인을 건너뛴다 — 창의 자원명 입력이 아직 연결되지 않았기 때문이며, HF30-3 이 입력을 살리고 빈 값을 거절한다.
	 */
	public void purgeForNudgeReplace(ResourceType resourceType, Long resourceId, String typedName) {
		Markable trashed = requireTrashed(resourceType, resourceId);
		if (typedName != null && !typedName.isBlank()) {
			TypedNameGuard.verify(trashed, typedName);
		}
		execute(PurgeRequest.forNudgeReplace(resourceType, resourceId, null, typedName));
	}

	private Markable requireTrashed(ResourceType resourceType, Long resourceId) {
		return inventoryFor(resourceType).findTrashedById(resourceId)
				.orElseThrow(PurgeTargetNotInTrashException::new);
	}

	private void execute(PurgeRequest request) {
		PurgeResult result = purgeExecutor.execute(request);
		log.info("[user-purge] origin={} type={} id={} result={}",
				request.origin(), request.resourceType(), request.resourceId(), result.getClass().getSimpleName());
		if (result instanceof PurgeResult.Failed failed) {
			Throwable cause = failed.cause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException(cause);
		}
	}

	private MarkableInventory inventoryFor(ResourceType resourceType) {
		return inventories.stream()
				.filter(inventory -> inventory.supportedType() == resourceType)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("지원하지 않는 자원 종류 : " + resourceType));
	}
}
