package com.example.serverprovision.management.os.service.comps;

import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E4-1-b-2 D-20 — ISO 등록 뒤 버전 확인 실패를 ISO 행에 남긴다. 추출 파이프라인은 트랜잭션 밖(@Async)에서 돌므로
 * 저장 경계를 이 빈이 갖는다. ISO 가 그새 사라졌으면 남길 곳이 없어 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class IsoRegistrationFailureRecorder {

    private final ISORepository isoRepository;

    @Transactional
    public void record(Long isoId, String reason) {
        isoRepository.findById(isoId).ifPresent(iso -> iso.markRegistrationFailed(reason));
    }
}
