package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.management.board.service.purge.BoardModelPurgeBlocker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 메인보드 영구 삭제를 막는 서버 하드웨어 기록(HF26). 회수된 서버의 기록도 센다 — 기록은 서버를 지워야 사라진다.
 */
@Component
@RequiredArgsConstructor
public class GuestServerBoardReference implements BoardModelPurgeBlocker {

	private final GuestServerDetailRepository guestServerDetailRepository;

	@Override
	public String remainingReference(Long boardModelId) {
		long count = guestServerDetailRepository.countByBoardModel_Id(boardModelId);
		return count == 0 ? null : "등록된 서버 " + count + " 대";
	}
}
