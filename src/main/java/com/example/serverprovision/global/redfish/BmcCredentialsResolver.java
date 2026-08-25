package com.example.serverprovision.global.redfish;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BMC 자격증명 후보 산출 (E1.5 D2, 사용자 확정 P1 · OQ2) — username 은 무조건 {@code admin}(기본값),
 * 비밀번호는 ① 사내 공장 표준 세팅 값(설정 {@code provision.bmc.password}) ② 401 이면 공장 기본
 * = <b>M/B 시리얼 11자</b>(실측 · 예외 없음 — 신품이 여기 걸린다) 순서로 시도한다.
 *
 * <p>표준 비밀번호 미설정이면 폴백만 후보가 된다. 시리얼도 없으면 빈 목록 — 호출자가 FAILED 결과로 답한다.
 * fail-fast 는 두지 않는다 — BMC 제어는 부가 기능이라 앱 부팅을 막을 이유가 없다.</p>
 */
@Component
public class BmcCredentialsResolver {

    private final String username;
    private final String standardPassword;

    public BmcCredentialsResolver(
            @Value("${provision.bmc.username:admin}") String username,
            @Value("${provision.bmc.password:}") String standardPassword) {
        this.username = username;
        this.standardPassword = standardPassword;
    }

    /** 시도 순서대로의 후보 — 표준 계정(설정돼 있을 때) → 공장 기본(시리얼이 있을 때). */
    public List<BmcCredentials> candidates(String boardSerial) {
        List<BmcCredentials> candidates = new ArrayList<>(2);
        standardCandidate().ifPresent(candidates::add);
        factoryCandidate(boardSerial).ifPresent(candidates::add);
        return candidates;
    }

    /** 표준 계정 한 벌 — 계정 표준화 사다리(E1.6 D-2)가 "어느 자격으로 열렸는가" 를 정체로 판정하는 재료. */
    public Optional<BmcCredentials> standardCandidate() {
        if (standardPassword == null || standardPassword.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BmcCredentials(username, standardPassword, "표준 계정"));
    }

    /** 공장 기본 한 벌 — 비밀번호 = 그 보드의 시리얼이라, 인증 성공 자체가 신원의 1차 증명이 된다(D-5). */
    public Optional<BmcCredentials> factoryCandidate(String boardSerial) {
        if (boardSerial == null || boardSerial.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BmcCredentials(username, boardSerial, "공장 기본(보드 시리얼)"));
    }
}
