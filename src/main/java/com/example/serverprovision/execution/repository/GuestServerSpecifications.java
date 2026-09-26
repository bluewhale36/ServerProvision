package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.dto.request.ServerListQuery;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.vo.ServerScope;
import com.example.serverprovision.global.web.list.LikePattern;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 게스트 서버 목록의 동적 필터(S8-2). 축마다 술어 하나 · 빈 축은 빠진다.
 *
 * <p>상세 · NIC · 진행은 모두 자식이 {@link GuestServer} 를 가리키고 역참조가 없다. 그래서 자식 조건은 상관 서브쿼리
 * {@code exists} 로 묻는다 — join 이면 NIC 수만큼 행이 불어 count 가 틀어진다.</p>
 *
 * <p>운영 상태는 저장 컬럼이 아니라 {@link GuestServerStatus#derive} 의 도출값이다. 여기 술어는 그 판정을 SQL 로 옮긴
 * 것이고, 두 판정이 어긋나지 않는지는 조합 전수 대조 테스트({@code GuestServerSpecificationsJpaTest})가 지킨다.</p>
 */
public final class GuestServerSpecifications {

    private GuestServerSpecifications() {
    }

    public static Specification<GuestServer> of(ServerListQuery q, ServerScope scope) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q.q() != null) {
                p.add(searchAny(root, query, cb, q.q()));
            }
            if (!q.phases().isEmpty()) {
                p.add(phaseIn(root, query, cb, q.phases()));
            }
            if (!q.statuses().isEmpty()) {
                p.add(cb.or(q.statuses().stream().map(s -> status(root, query, cb, s)).toArray(Predicate[]::new)));
            }
            if (!q.includeDecommissioned()) {
                p.add(cb.isNull(root.get("decommissionedAt")));
            }
            if (q.boardModelId() != null) {
                p.add(existsDetail(root, query, cb, (d, c) -> c.equal(d.get("boardModel").get("id"), q.boardModelId())));
            }
            if (q.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.from().atStartOfDay()));
            }
            if (q.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), q.to().plusDays(1).atStartOfDay()));
            }
            if (scope.onlyIds() != null) {
                p.add(scope.onlyIds().isEmpty() ? cb.disjunction() : root.get("id").in(scope.onlyIds()));
            }
            if (!scope.excludeIds().isEmpty()) {
                p.add(cb.not(root.get("id").in(scope.excludeIds())));
            }
            return p.isEmpty() ? cb.conjunction() : cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** 이름 · SMBIOS UUID · 시리얼 · 대표 NIC IP · BMC IP · 보드 모델명 중 하나라도 부분 일치(OR). */
    private static Predicate searchAny(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb, String q) {
        String pattern = LikePattern.contains(q);
        return cb.or(
                like(cb, root.get("name").as(String.class), pattern),
                like(cb, root.get("systemUUID").as(String.class), pattern),
                like(cb, root.get("serialNumber").as(String.class), pattern),
                existsPrimaryNic(root, query, cb, (n, c) -> like(c, n.get("ipAddress").as(String.class), pattern)),
                existsDetail(root, query, cb, (d, c) -> c.or(
                        like(c, d.get("bmcIp").as(String.class), pattern),
                        like(c, d.get("boardModel").get("modelName").as(String.class), pattern))));
    }

    private static Predicate like(CriteriaBuilder cb, jakarta.persistence.criteria.Expression<String> value, String pattern) {
        return cb.like(cb.lower(value), pattern, LikePattern.ESCAPE);
    }

    /** 현재 단계가 고른 phase 들 중 하나(OR). step → phase 매핑은 {@link ProvisioningPhaseStep#getPhaseType()} 가 SSOT. */
    private static Predicate phaseIn(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                     Set<ProvisioningPhase> phases) {
        List<ProvisioningPhaseStep> steps = Arrays.stream(ProvisioningPhaseStep.values())
                .filter(s -> phases.contains(s.getPhaseType()))
                .toList();
        return existsProgress(root, query, cb, (pr, c) -> pr.get("currentStep").in(steps));
    }

    /**
     * 운영 상태 한 가지 — {@link GuestServerStatus#derive} 의 우선순위(회수 → 실패 → 완료 → 등록됨 → 프로비저닝 중)를
     * 그대로 옮긴다. 등록됨은 "시작 · 실패 · 완료 신호가 모두 없음" 이다 — 시작 전에 실패한 서버(R13 미개시 실패)는
     * derive 가 실패로 보므로 등록됨에서 빠져야 한다.
     */
    static Predicate status(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb, GuestServerStatus status) {
        Predicate active = cb.isNull(root.get("decommissionedAt"));
        return switch (status) {
            case DECOMMISSIONED -> cb.isNotNull(root.get("decommissionedAt"));
            case FAILED -> cb.and(active, existsProgress(root, query, cb, (p, c) -> c.isNotNull(p.get("failedAt"))));
            case PROVISIONED -> cb.and(active, existsProgress(root, query, cb,
                    (p, c) -> c.and(c.isNull(p.get("failedAt")), c.isNotNull(p.get("completedAt")))));
            case REGISTERED -> cb.and(active, cb.not(existsProgress(root, query, cb, (p, c) -> c.or(
                    c.isNotNull(p.get("startedAt")), c.isNotNull(p.get("failedAt")), c.isNotNull(p.get("completedAt"))))));
            case PROVISIONING -> cb.and(active, existsProgress(root, query, cb, (p, c) -> c.and(
                    c.isNotNull(p.get("startedAt")), c.isNull(p.get("failedAt")), c.isNull(p.get("completedAt")))));
        };
    }

    private static Predicate existsProgress(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                            BiFunction<Root<ProvisioningProgress>, CriteriaBuilder, Predicate> condition) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<ProvisioningProgress> p = sub.from(ProvisioningProgress.class);
        sub.select(cb.literal(1)).where(cb.equal(p.get("guestServer"), root), condition.apply(p, cb));
        return cb.exists(sub);
    }

    private static Predicate existsDetail(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                          BiFunction<Root<GuestServerDetail>, CriteriaBuilder, Predicate> condition) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<GuestServerDetail> d = sub.from(GuestServerDetail.class);
        sub.select(cb.literal(1)).where(cb.equal(d.get("guestServer"), root), condition.apply(d, cb));
        return cb.exists(sub);
    }

    private static Predicate existsPrimaryNic(Root<GuestServer> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                              BiFunction<Root<HostNicBinding>, CriteriaBuilder, Predicate> condition) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<HostNicBinding> n = sub.from(HostNicBinding.class);
        sub.select(cb.literal(1)).where(cb.equal(n.get("guestServer"), root), cb.isTrue(n.get("isPrimary")),
                condition.apply(n, cb));
        return cb.exists(sub);
    }
}
