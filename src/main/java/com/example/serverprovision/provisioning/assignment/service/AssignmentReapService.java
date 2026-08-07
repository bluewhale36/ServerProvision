package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재할당이 만들어내는 <b>미소비 supersede 스냅샷</b>의 지연 purge write-side(U3-2-a). 재할당은 기존 활성을 즉시
 * 지우지 않고 supersede(논리 종료)로 이력을 남긴다 — 그중 <b>한 번도 개시 안 된</b> 행은 순수 쓰레기라 TTL 이 지나
 * 아무도 안 건드릴 때 물리 삭제한다({@code OrphanQuarantineService#purgeExpired} 동형).
 *
 * <p>수거 술어(DA3)는 {@code supersededAt IS NOT NULL AND consumedAt IS NULL AND supersededAt < (now - TTL)}.
 * 소비된(개시됐던) supersede 행은 감사 가치가 있어 보존하고, 활성 행은 술어에서 제외된다. TTL 지연은 재할당 직후
 * 즉시 삭제 시 개시 tx 와 경합할 위험을 없앤다 — reaper 는 supersede 행만 지우고 {@code markConsumed} 는 활성 행만
 * 건드리므로 경합 원천이 애초에 없다. {@code @Transactional} 로 배치 전체를 한 tx 에 묶는다(파일 IO 없는 순수 DB
 * 삭제라 부분 실패 개별 처리가 불필요 — 무결성 문제는 롤백으로 정직하게 드러난다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentReapService {

    private final SettingAssignmentRepository assignmentRepository;

    /** 미소비 supersede 행 물리 삭제 TTL. 재할당 직후 개시 tx 와의 경합을 넘길 만큼만 지연하면 된다. */
    @Value("${assignment.reap.ttl:24h}")
    private Duration ttl;

    /**
     * 미소비 supersede 스냅샷 중 TTL 경과분을 hard-delete 한다(reaper 트리거가 호출). 자동화 경로이며 TTL 이
     * 의도 검증을 대신하므로 typed-name 가드는 없다. {@code assigned_process} 자식은 JPA cascade + DB ON DELETE
     * CASCADE 로 함께 지워진다.
     *
     * @return 수거한 스냅샷 건수
     */
    @Transactional
    public int purgeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minus(ttl);
        List<SettingAssignment> expired = assignmentRepository
                .findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(threshold);
        if (!expired.isEmpty()) {
            assignmentRepository.deleteAll(expired);
            log.info("[assignment-reaper] 미소비 supersede 스냅샷 {} 건 수거(hard-delete). ttl={}", expired.size(), ttl);
        }
        return expired.size();
    }
}
