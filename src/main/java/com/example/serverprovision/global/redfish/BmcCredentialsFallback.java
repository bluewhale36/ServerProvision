package com.example.serverprovision.global.redfish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 자격증명 폴백 순회(E1.5 P1 의 공통화, E2-2 에서 두 번째 사용처가 생겨 추출) — 후보를 순서대로 시도하고
 * <b>401 일 때만</b> 다음 후보로 넘어간다. 연결 불가나 프로토콜 오류는 자격증명과 무관하므로 그 자리에서 멈춘다.
 *
 * <p>실패를 결과 객체로 바꾸는 일은 여기서 하지 않는다 — 전원 제어는 {@code PowerControlResult} 로,
 * 펌웨어 집행은 상태 기계의 입력으로 각자 다르게 변환하기 때문이다. 공통인 것은 <b>순회 규칙</b>뿐이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcCredentialsFallback {

    private final BmcCredentialsResolver credentialsResolver;

    /**
     * 후보를 순서대로 시도한다.
     *
     * @throws RedfishRequestException 후보가 없거나(AUTH_FAILED) 모든 후보가 실패했을 때 — 마지막 실패를 그대로 던진다
     */
    public <T> T attempt(RedfishTarget target, Function<BmcCredentials, T> call) {
        List<BmcCredentials> candidates = credentialsResolver.candidates(target.boardSerial());
        if (candidates.isEmpty()) {
            throw new RedfishRequestException(RedfishError.AUTH_FAILED,
                    "BMC 자격증명이 없습니다 — 표준 비밀번호가 비어 있고 보드 시리얼도 수집되지 않았습니다.", null);
        }
        RedfishRequestException last = null;
        for (BmcCredentials credentials : candidates) {
            try {
                return call.apply(credentials);
            } catch (RedfishRequestException e) {
                last = e;
                if (e.getError() != RedfishError.AUTH_FAILED) {
                    break;
                }
                log.info("[redfish] {} — {} 자격증명 거부(401) → 다음 후보로", target.bmcIp(), credentials.source());
            }
        }
        throw last;
    }
}
