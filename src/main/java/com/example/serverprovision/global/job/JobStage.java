package com.example.serverprovision.global.job;

/**
 * 백그라운드 Job 파이프라인의 단계 타입 마커. 도메인별 enum 이 이 인터페이스를 구현해
 * "단계 라벨" 을 한 곳에서 선언한다.
 *
 * <p>chunk progress bar 도입 (사용자 결정) 으로 진행률 percent 는 더 이상 보고하지 않는다 —
 * 단계 진입 / 완료 / 실패 의 3 가지 이벤트만 {@code BackgroundJobService} 에 보고하면
 * 프론트가 chunk 색상으로 렌더링한다.</p>
 *
 * <p>도메인 enum 의 {@code values()} 가 그 Job 의 stages 리스트가 된다 — 등록 시점에
 * {@code Stage.values()} 의 label 들을 그대로 넘기면 끝.</p>
 */
public interface JobStage {

	/**
	 * 알림 카드의 chunk 위에 노출되는 단계 이름.
	 */
	String label();
}
