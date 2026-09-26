package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.dto.request.ServerListQuery;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.execution.vo.ServerScope;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8-2 — 게스트 서버 목록 술어가 실제 SQL(H2)로 도는지 본다. 핵심은 운영 상태 술어가 도출 규칙
 * {@link GuestServerStatus#derive} 와 <b>같은 답</b>을 내는지다 — 진행 신호(시작 · 실패 · 완료)의 모든 조합과 회수 여부를
 * 서버로 만들고, 상태마다 SQL 이 고른 서버 집합과 derive 가 그 상태로 판정한 집합을 대조한다(드리프트 0).
 */
@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@ActiveProfiles("repo-test")
class GuestServerSpecificationsJpaTest {

    @Autowired GuestServerRepository repository;
    @Autowired EntityManager em;

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 10, 12, 0);

    private GuestServer server(String name) {
        GuestServer s = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).name(name).build();
        em.persist(s);
        return s;
    }

    private ProvisioningProgress progress(GuestServer s, ProvisioningPhaseStep step, boolean started, boolean failed, boolean completed) {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(s).currentStep(step).lastTransitionAt(T)
                .startedAt(started ? T : null).failedAt(failed ? T : null).completedAt(completed ? T : null).build();
        em.persist(p);
        return p;
    }

    private void decommission(GuestServer s) {
        em.createNativeQuery("update guest_server set decommissioned_at = ? where id = ?")
                .setParameter(1, T).setParameter(2, s.getId()).executeUpdate();
    }

    private void createdAt(GuestServer s, LocalDateTime at) {
        em.createNativeQuery("update guest_server set created_at = ? where id = ?")
                .setParameter(1, at).setParameter(2, s.getId()).executeUpdate();
    }

    private BoardModel board(String name) {
        BoardModel b = BoardModel.builder().vendor(Vendor.GIGABYTE).modelName(name).build();
        em.persist(b);
        return b;
    }

    private static ServerListQuery query(String q, Set<ProvisioningPhase> phases, Boolean includeDecommissioned,
                                         Long board, LocalDate from, LocalDate to) {
        return new ServerListQuery(null, q, phases, null, includeDecommissioned, board, from, to, null, null);
    }

    private List<String> names(Specification<GuestServer> spec) {
        return repository.findAll(spec).stream().map(GuestServer::getName).sorted().toList();
    }

    @Test
    @DisplayName("운영 상태 술어 ↔ GuestServerStatus.derive — 진행 없음 + 시작 · 실패 · 완료 8 조합, 각각 회수 여부 2 → 18 대 전수 일치")
    void statusPredicatesMatchDerive() {
        Map<String, GuestServerStatus> expected = new HashMap<>();
        List<Object[]> combos = new ArrayList<>();
        combos.add(new Object[]{"none", null});
        for (int bits = 0; bits < 8; bits++) {
            combos.add(new Object[]{"p" + bits, bits});
        }
        for (Object[] c : combos) {
            for (boolean decommissioned : new boolean[]{false, true}) {
                String name = c[0] + (decommissioned ? "-d" : "");
                GuestServer s = server(name);
                ProvisioningProgress p = null;
                if (c[1] != null) {
                    int bits = (Integer) c[1];
                    p = progress(s, ProvisioningPhaseStep.BIOS_UPDATING, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
                }
                expected.put(name, GuestServerStatus.derive(p, decommissioned ? T : null));
                if (decommissioned) {
                    decommission(s);
                }
            }
        }
        em.flush();
        em.clear();

        for (GuestServerStatus status : GuestServerStatus.values()) {
            List<String> bySql = names((root, q, cb) -> GuestServerSpecifications.status(root, q, cb, status));
            List<String> byDerive = expected.entrySet().stream().filter(e -> e.getValue() == status)
                    .map(Map.Entry::getKey).sorted().toList();
            assertThat(bySql).as("상태 %s", status).isEqualTo(byDerive);
        }
        // 미개시 실패(started=0 · failed=1 → bits 2)는 등록됨이 아니라 실패다 — CP1 에서 정정한 지점
        assertThat(expected.get("p2")).isEqualTo(GuestServerStatus.FAILED);
    }

    @Test
    @DisplayName("상태 다중 선택은 축 안 OR · 기본은 회수 제외 · 회수 포함을 켜면 회수 서버도 나온다")
    void statusOrAndDecommissionedAxis() {
        progress(server("failed"), ProvisioningPhaseStep.BIOS_UPDATING, true, true, false);
        progress(server("done"), ProvisioningPhaseStep.TESTING, true, false, true);
        server("fresh");
        GuestServer gone = server("gone");
        em.flush();
        decommission(gone);
        em.clear();

        ServerListQuery failedOrDone = new ServerListQuery(null, null, null,
                Set.of(GuestServerStatus.FAILED, GuestServerStatus.PROVISIONED), null, null, null, null, null, null);
        assertThat(names(GuestServerSpecifications.of(failedOrDone, ServerScope.ALL))).containsExactly("done", "failed");
        assertThat(names(GuestServerSpecifications.of(query(null, null, null, null, null, null), ServerScope.ALL)))
                .containsExactly("done", "failed", "fresh");
        assertThat(names(GuestServerSpecifications.of(query(null, null, true, null, null, null), ServerScope.ALL)))
                .containsExactly("done", "failed", "fresh", "gone");
    }

    @Test
    @DisplayName("진행 단계 다중 선택은 OR — step → phase 매핑으로 판정하고, 진행이 없는 서버는 어느 단계에도 들지 않는다")
    void phaseIsOred() {
        progress(server("flash"), ProvisioningPhaseStep.BMC_UPDATING, true, false, false);
        progress(server("setting"), ProvisioningPhaseStep.BIOS_SETTING, true, false, false);
        progress(server("diag"), ProvisioningPhaseStep.INFORMATION_COLLECTING, false, false, false);
        server("none");
        em.flush();
        em.clear();

        assertThat(names(GuestServerSpecifications.of(query(null,
                Set.of(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.FIRMWARE_SETTING), null, null, null, null), ServerScope.ALL)))
                .containsExactly("flash", "setting");
    }

    @Test
    @DisplayName("검색은 이름 · UUID · 시리얼 · 대표 NIC IP · BMC IP · 보드 모델명 중 하나라도 부분 일치(대소문자 무시)")
    void searchSixFields() {
        BoardModel ms04 = board("MS04-CE0");
        GuestServer a = server("alpha");
        GuestServer b = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.fromString("5a8e0000-0000-0000-0000-00000000beef"))
                .name("bravo").serialNumber("SN-XY77").build();
        em.persist(b);
        GuestServer c = server("charlie");
        em.persist(HostNicBinding.builder().id(UUID.randomUUID()).guestServer(c).macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:01"))
                .ipAddress(IpAddressVO.of("10.1.1.55")).ipSource(IpSource.DHCP).isPrimary(true).build());
        GuestServer d = server("delta");
        em.persist(HostNicBinding.builder().id(UUID.randomUUID()).guestServer(d).macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:02"))
                .ipAddress(IpAddressVO.of("10.1.1.56")).ipSource(IpSource.DHCP).isPrimary(false).build());   // 대표 아님
        GuestServer e = server("echo");
        em.persist(GuestServerDetail.builder().id(UUID.randomUUID()).guestServer(e).boardModel(ms04).discoveryStage(DiscoveryStage.DIAGNOSTIC_ENRICHED)
                .bmcIp(IpAddressVO.of("10.1.1.9")).build());
        em.flush();
        em.clear();

        assertThat(names(GuestServerSpecifications.of(query("ALPH", null, null, null, null, null), ServerScope.ALL))).containsExactly("alpha");
        assertThat(names(GuestServerSpecifications.of(query("beef", null, null, null, null, null), ServerScope.ALL))).containsExactly("bravo");
        assertThat(names(GuestServerSpecifications.of(query("xy77", null, null, null, null, null), ServerScope.ALL))).containsExactly("bravo");
        assertThat(names(GuestServerSpecifications.of(query("1.1.5", null, null, null, null, null), ServerScope.ALL))).containsExactly("charlie");
        assertThat(names(GuestServerSpecifications.of(query("10.1.1.9", null, null, null, null, null), ServerScope.ALL))).containsExactly("echo");
        assertThat(names(GuestServerSpecifications.of(query("ms04", null, null, null, null, null), ServerScope.ALL))).containsExactly("echo");
        assertThat(names(GuestServerSpecifications.of(query(null, null, null, ms04.getId(), null, null), ServerScope.ALL))).containsExactly("echo");
    }

    @Test
    @DisplayName("등록 기간은 날짜 단위 · 양 끝 포함, 거꾸로면 결과가 빈다")
    void createdRangeInclusive() {
        GuestServer early = server("aug31");
        GuestServer first = server("sep01");
        GuestServer last = server("sep30-late");
        GuestServer after = server("oct01");
        em.flush();
        createdAt(early, LocalDateTime.of(2026, 8, 31, 23, 59));
        createdAt(first, LocalDateTime.of(2026, 9, 1, 0, 0));
        createdAt(last, LocalDateTime.of(2026, 9, 30, 23, 59));
        createdAt(after, LocalDateTime.of(2026, 10, 1, 0, 0));
        em.clear();

        LocalDate from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 9, 30);
        assertThat(names(GuestServerSpecifications.of(query(null, null, null, null, from, to), ServerScope.ALL)))
                .containsExactly("sep01", "sep30-late");
        assertThat(names(GuestServerSpecifications.of(query(null, null, null, null, to, from), ServerScope.ALL))).isEmpty();
    }

    @Test
    @DisplayName("범위(ServerScope) — 이 id 들만 · 이 id 들 빼고 · 빈 허용 집합은 결과 없음")
    void scopeIncludeExclude() {
        GuestServer a = server("a");
        GuestServer b = server("b");
        server("c");
        em.flush();
        em.clear();

        ServerListQuery all = query(null, null, null, null, null, null);
        assertThat(names(GuestServerSpecifications.of(all, ServerScope.only(Set.of(a.getId(), b.getId()))))).containsExactly("a", "b");
        assertThat(names(GuestServerSpecifications.of(all, ServerScope.excluding(Set.of(a.getId()))))).containsExactly("b", "c");
        assertThat(names(GuestServerSpecifications.of(all, ServerScope.only(Set.of())))).isEmpty();
    }
}
