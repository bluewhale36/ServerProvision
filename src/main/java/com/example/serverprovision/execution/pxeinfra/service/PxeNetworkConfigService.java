package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.execution.pxeinfra.apply.ApplyOutcome;
import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.repository.PxeNetworkConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PXE 네트워크 구성의 트랜잭션 경계. 이 테이블은 고정 싱글턴(최대 1행)이라 저장은 {@code findFirst} 기반 upsert 로
 * 한 벌만 유지한다. 적용 파이프라인의 귀결({@link ApplyOutcome})을 성공·실패 무관하게 desired 와 함께 기록한다.
 *
 * <p>실패 귀결의 HTTP 승격({@link ApplyOutcome#throwIfNotApplied})은 이 트랜잭션 <b>밖</b>에서 호출자가 한다 —
 * 여기서 던지면 기록이 롤백돼 실패 자체가 사라지기 때문이다(D3). 그래서 save 는 정상 리턴만 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class PxeNetworkConfigService {

    private final PxeNetworkConfigRepository repository;
    private final PxeNetworkConfigMapper mapper;

    /** 저장된 유일한 구성 행(최초 저장 전이면 empty). */
    @Transactional(readOnly = true)
    public Optional<PxeNetworkConfig> load() {
        return repository.findFirstByOrderByIdAsc();
    }

    /**
     * 요청의 desired 를 단일 행에 upsert 하고 적용 귀결을 기록한다(성공·실패 모두). throwIfNotApplied 는 호출하지
     * 않는다 — 실패도 정상 리턴으로 커밋시키고, 승격은 트랜잭션 밖에서 한다.
     */
    @Transactional
    public PxeNetworkConfig save(PxeNetworkConfigRequest req, ApplyOutcome outcome) {
        PxeNetworkConfig config = repository.findFirstByOrderByIdAsc()
                .map(existing -> {
                    mapper.applyTo(existing, req);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(req));
        config.recordApply(outcome.result(), outcome.appliedVersionId(), LocalDateTime.now());
        return repository.save(config);
    }
}
