package com.example.serverprovision.provisioning.setting.repository;

import com.example.serverprovision.global.web.list.Paging;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SettingSortField;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8-1 — 정의서 목록 술어가 실제 SQL(H2)로 도는지 본다. 단위 · MVC 테스트는 저장소를 mock 으로 두므로
 * LIKE 이스케이프 · 상관 서브쿼리 exists · count 쿼리는 여기서만 검증된다. MariaDB 고유 동작은 CP5 샌드박스가 본다.
 */
// 정의서 payload 의 JSON 변환기가 ObjectMapper 를 주입받는다 — JPA 슬라이스는 Jackson 자동 설정을 싣지 않으므로 얹는다.
@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@ActiveProfiles("repo-test")
class SettingSpecificationsJpaTest {

    @Autowired SettingDefinitionRepository repository;

    private static SettingProcess update() {
        return new SettingProcess(new ProcessPayload(new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null))));
    }

    private static SettingProcess setting() {
        return new SettingProcess(new ProcessPayload(new BasicSettingRequest(List.of())));
    }

    private SettingDefinition save(String name, SettingProcess... processes) {
        return repository.save(SettingDefinition.builder().name(name).processes(new ArrayList<>(List.of(processes))).build());
    }

    @BeforeEach
    void seed() {
        save("8월 표준 세팅", update(), setting());          // 두 단계
        save("펌웨어만", update());                            // 한 단계
        save("50%_할인", setting());                          // 와일드카드 글자를 이름에 품은 정의서
        SettingDefinition off = save("비활성 표준", update());
        off.toggleEnabled();
        SettingDefinition gone = save("삭제된 표준", update(), setting());
        gone.softDelete();
        repository.flush();
    }

    private Page<SettingDefinition> search(SettingListQuery q) {
        return repository.findAll(SettingSpecifications.of(q), Paging.of(PageRequest.of(0, 20), q.sort(), q.dir()));
    }

    private static SettingListQuery query(String q, Boolean enabled, Set<SettingProcessType> types, Boolean includeDeleted) {
        return new SettingListQuery(q, enabled, null, types, includeDeleted, SettingSortField.NAME, Sort.Direction.ASC);
    }

    @Test
    @DisplayName("기본 — 삭제분은 빠지고 나머지 4 건")
    void excludesDeletedByDefault() {
        assertThat(search(query(null, null, null, null)).getTotalElements()).isEqualTo(4);
        assertThat(search(query(null, null, null, true)).getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("이름 검색 — 대소문자 무시 부분 일치 · % _ 는 글자 그대로")
    void nameSearchEscapesWildcards() {
        assertThat(search(query("표준", null, null, null)).map(SettingDefinition::getName).getContent())
                .containsExactly("8월 표준 세팅", "비활성 표준");
        assertThat(search(query("%_", null, null, null)).map(SettingDefinition::getName).getContent())
                .containsExactly("50%_할인");
        assertThat(search(query("_", null, null, null)).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("단계 다중 선택은 모두 포함(AND) — 총 건수가 단계 수만큼 부풀지 않는다")
    void processTypesAreAnded() {
        Page<SettingDefinition> both = search(query(null, null,
                Set.of(SettingProcessType.BASIC_UPDATE, SettingProcessType.BASIC_SETTING), null));
        Page<SettingDefinition> updateOnly = search(query(null, null, Set.of(SettingProcessType.BASIC_UPDATE), null));

        assertThat(both.map(SettingDefinition::getName).getContent()).containsExactly("8월 표준 세팅");
        assertThat(both.getTotalElements()).isEqualTo(1);
        assertThat(updateOnly.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("활성 축 + 이름 역순 정렬")
    void enabledAxisAndSort() {
        SettingListQuery disabled = new SettingListQuery(null, false, null, null, null, SettingSortField.NAME, Sort.Direction.DESC);
        assertThat(search(disabled).map(SettingDefinition::getName).getContent()).containsExactly("비활성 표준");

        SettingListQuery byNameDesc = new SettingListQuery(null, null, null, null, null, SettingSortField.NAME, Sort.Direction.DESC);
        assertThat(search(byNameDesc).map(SettingDefinition::getName).getContent())
                .containsExactly("펌웨어만", "비활성 표준", "8월 표준 세팅", "50%_할인");
    }
}
