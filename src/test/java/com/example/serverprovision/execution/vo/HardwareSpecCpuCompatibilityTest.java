package com.example.serverprovision.execution.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPU 소켓 구조 변경의 하위 호환 (U3-3 DEC-C).
 *
 * <p>여기서 검증하는 것은 <b>저장된 JSON 을 읽는 경로</b>다. DEC-C 이전에 적재된 행은
 * {@code "cpu": {...}} 단수 객체이고, 목록 · 상세는 그 행도 정상으로 보여줘야 한다.
 * 이 호환이 {@code HardwareSpec} 안에 있어야 하는 이유는 진단 보고 파싱과 저장분 읽기가
 * <b>서로 다른 경로</b>이기 때문이다 — 파서만 고치면 이미 저장된 행이 빈 CPU 로 보인다.</p>
 */
class HardwareSpecCpuCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("신 형식 cpuSockets 배열을 그대로 읽는다")
    void readsNewSocketArray() {
        String json = """
                {"cpuSockets":[
                   {"slot":"CPU1","manufacturer":"Intel","model":"Xeon Gold 6338"},
                   {"slot":"CPU2","manufacturer":"Intel","model":"Xeon Gold 6338"}]}""";

        HardwareSpec spec = objectMapper.readValue(json, HardwareSpec.class);

        assertThat(spec.cpuSockets()).hasSize(2);
        assertThat(spec.cpuSockets().getFirst().slot()).isEqualTo("CPU1");
        assertThat(spec.cpuSockets().getLast().model()).isEqualTo("Xeon Gold 6338");
    }

    @Test
    @DisplayName("구 형식 cpu 단수 객체는 1소켓으로 승급해 읽는다 — 마이그레이션 없이 넘어간다")
    void promotesLegacySingleCpu() {
        String json = """
                {"cpu":{"manufacturer":"Intel","model":"Xeon Gold 6338"}}""";

        HardwareSpec spec = objectMapper.readValue(json, HardwareSpec.class);

        assertThat(spec.cpuSockets()).hasSize(1);
        assertThat(spec.cpuSockets().getFirst().model()).isEqualTo("Xeon Gold 6338");
        assertThat(spec.cpuSockets().getFirst().slot()).isNull();
    }

    @Test
    @DisplayName("신 형식이 있으면 구 형식은 무시한다 — 재수집된 행이 옛 값에 덮이지 않는다")
    void newFormWinsOverLegacy() {
        String json = """
                {"cpuSockets":[{"slot":"CPU1","manufacturer":"AMD","model":"EPYC 7763"}],
                 "cpu":{"manufacturer":"Intel","model":"Xeon Gold 6338"}}""";

        HardwareSpec spec = objectMapper.readValue(json, HardwareSpec.class);

        assertThat(spec.cpuSockets()).hasSize(1);
        assertThat(spec.cpuSockets().getFirst().model()).isEqualTo("EPYC 7763");
    }

    @Test
    @DisplayName("둘 다 없으면 null — 수집 전 서버는 스펙 그룹 대상이 아니므로 키를 만들지 않는다")
    void bothAbsentYieldsNull() {
        HardwareSpec spec = objectMapper.readValue("{\"disks\":[]}", HardwareSpec.class);

        assertThat(spec.cpuSockets()).isNull();
    }
}
