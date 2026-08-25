package com.example.serverprovision.global.redfish;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 성공 자격 캐시(E1.6 D-3) — 보드 시리얼별로 마지막에 성공한 후보 source 를 기억해, 다음 폴백 순회가
 * 그 후보부터 시도하게 한다. 신품(공장 기본만 유효)에 매 폴링마다 표준 계정 401 이 쌓이는 노이즈를 없앤다.
 * 인메모리인 이유 — 재기동 시 잃는 것이 첫 호출 한 번의 401 뿐이라 영속(DDL)의 가치가 없다.
 */
@Component
public class BmcCredentialsMemory {

    private final Map<String, String> lastSuccessfulSource = new ConcurrentHashMap<>();

    public void remember(String boardSerial, String source) {
        if (boardSerial != null && !boardSerial.isBlank() && source != null) {
            lastSuccessfulSource.put(boardSerial, source);
        }
    }

    /** 기억된 source 를 앞세운 사본 — 시리얼이 없거나 기억이 없으면 원본 그대로. */
    public List<BmcCredentials> preferredOrder(String boardSerial, List<BmcCredentials> candidates) {
        if (boardSerial == null || boardSerial.isBlank()) {
            return candidates;
        }
        String remembered = lastSuccessfulSource.get(boardSerial);
        if (remembered == null) {
            return candidates;
        }
        List<BmcCredentials> reordered = new ArrayList<>(candidates.size());
        for (BmcCredentials candidate : candidates) {
            if (remembered.equals(candidate.source())) {
                reordered.addFirst(candidate);
            } else {
                reordered.add(candidate);
            }
        }
        return reordered;
    }
}
