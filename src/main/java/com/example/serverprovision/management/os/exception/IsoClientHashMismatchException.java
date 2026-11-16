package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * MK2 WAVE 3 — 단계 B (server-side hash 재계산) 결과가 client 가 intent 시 보낸 hash 와 다른 경우.
 *
 * <p>두 가지 시나리오 :
 * <ul>
 *   <li>네트워크 / 디스크 corruption — 업로드 중 파일 손상</li>
 *   <li>client 변조 — 의도적으로 가짜 hash 를 보낸 경우 (다른 자원의 hash 로 nudge 회피 시도 등)</li>
 * </ul>
 *
 * <p>두 케이스 모두 데이터 무결성 위반이므로 fail-fast (400) + 임시 파일 cleanup + 감사 로그.
 * 사용자에게는 "업로드 중 파일이 손상되었거나 client 가 보낸 fingerprint 가 일치하지 않습니다." 안내.</p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IsoClientHashMismatchException extends DomainException implements RegistrationFailure {

	public IsoClientHashMismatchException(String clientHash, String serverHash) {
		// 두 해시 값은 던지는 쪽(IsoRegistrationService.finalize)이 로그에 남긴다 — 화면 문장에는 싣지 않는다.
		super("업로드한 파일의 해시가 사전 검증 값과 다릅니다. 전송 중 파일이 손상되었을 수 있으니 다시 업로드해야 합니다.");
	}

	/** 콘텐츠/영구 실패 — finalize 가 이미 업로드 파일을 정리했으므로 격리하지 않는다. */
	@Override
	public FailureDisposition disposition() {
		return FailureDisposition.CLEANUP;
	}

}
