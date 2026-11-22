package com.example.serverprovision.execution.engine.rhel.ossetting;

import com.example.serverprovision.execution.engine.rhel.ossetting.OsSettingItemOutcome.Kind;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 정의서의 OS 후처리와 첫 부팅 관측을 비교한 판정(E4-1-b-7). 기본 설정은 관측이 정의서와 같아야 하고, 스크립트는 종료 코드가 0 이어야
 * 한다. 하나라도 어긋나면 실패이고 어긋난 항목만 사유에 적는다. 스크립트 성공은 실행이 끝났다는 뜻일 뿐이라 직접 확인 안내를 붙인다.
 *
 * @param items 관측 묶음을 받지 못했으면 비어 있다
 */
public record OsSettingVerdict(boolean passed, List<OsSettingItemOutcome> items, String detail, String message) {

    static final String MISSING_OBSERVATION = "OS 후처리 관측값을 받지 못했습니다.";
    static final String CONFIRM_MANUALLY = "스크립트 실행은 끝났지만 적용 여부는 서버에서 직접 확인해야 합니다.";

    public OsSettingVerdict {
        items = List.copyOf(items);
    }

    public static OsSettingVerdict judge(RhelOsSettingPlan plan, Optional<OsSettingObservation> observation) {
        if (observation.isEmpty()) {
            return new OsSettingVerdict(false, List.of(), MISSING_OBSERVATION, MISSING_OBSERVATION);
        }
        OsSettingObservation seen = observation.get();
        List<OsSettingItemOutcome> items = new ArrayList<>();
        items.add(selinux(plan.selinux(), seen.selinux()));
        plan.services().forEach(service -> items.add(service(service, seen.serviceState(service.name()))));
        plan.packages().forEach(name -> items.add(pkg(name, seen.isInstalled(name))));
        for (int no = 1; no <= plan.scripts().size(); no++) {
            items.add(script(no, plan.scripts().get(no - 1).fileName(), seen.exitCodeOf(no)));
        }
        List<String> failures = items.stream().filter(item -> !item.ok()).map(OsSettingItemOutcome::failure).toList();
        if (!failures.isEmpty()) {
            String detail = String.join(" ", failures);
            return new OsSettingVerdict(false, items, detail, detail);
        }
        String notice = plan.scripts().isEmpty() ? null : CONFIRM_MANUALLY;
        return new OsSettingVerdict(true, items, notice == null ? "" : notice, notice);
    }

    private static OsSettingItemOutcome selinux(SelinuxMode expected, String observed) {
        boolean ok = SelinuxMode.of(observed).filter(expected::equals).isPresent();
        return ok
                ? OsSettingItemOutcome.passed(Kind.SELINUX, "SELinux", expected.wire(), observed)
                : OsSettingItemOutcome.failed(Kind.SELINUX, "SELinux", expected.wire(), observed,
                        format("SELinux 모드가 다릅니다(정의서 {0}, 관측 {1}).", expected.wire(), shown(observed)));
    }

    private static OsSettingItemOutcome service(ServiceDirective service, ServiceEnablement observed) {
        String expected = service.intent() == ServiceIntent.ENABLE ? "enabled" : "disabled";
        String seen = observed.wire();
        if (service.intent().isSatisfiedBy(observed)) {
            return OsSettingItemOutcome.passed(Kind.SERVICE, service.name(), expected, seen);
        }
        String pattern = service.intent() == ServiceIntent.ENABLE
                ? "서비스 {0} 의 상태가 활성화가 아닙니다(관측 {1})."
                : "서비스 {0} 의 상태가 비활성화가 아닙니다(관측 {1}).";
        return OsSettingItemOutcome.failed(Kind.SERVICE, service.name(), expected, seen, format(pattern, service.name(), seen));
    }

    private static OsSettingItemOutcome pkg(String name, boolean installed) {
        return installed
                ? OsSettingItemOutcome.passed(Kind.PACKAGE, name, "installed", "installed")
                : OsSettingItemOutcome.failed(Kind.PACKAGE, name, "installed", "missing",
                        format("패키지 {0} 의 설치를 확인하지 못했습니다.", name));
    }

    private static OsSettingItemOutcome script(int no, String fileName, Optional<Integer> exitCode) {
        String label = no + ". " + fileName;
        if (exitCode.isEmpty()) {
            return OsSettingItemOutcome.failed(Kind.SCRIPT, label, null, null,
                    format("스크립트 {0}번({1})의 실행 기록이 없습니다.", no, fileName));
        }
        int code = exitCode.get();
        return code == 0
                ? OsSettingItemOutcome.passed(Kind.SCRIPT, label, null, "0")
                : OsSettingItemOutcome.failed(Kind.SCRIPT, label, null, String.valueOf(code),
                        format("스크립트 {0}번({1})의 종료 코드가 {2} 입니다.", no, fileName, String.valueOf(code)));
    }

    private static String shown(String observed) {
        return observed == null || observed.isBlank() ? "-" : observed;
    }

    /** 번호가 천 단위 쉼표로 바뀌지 않도록 인자를 문자열로 넘긴다. */
    private static String format(String pattern, Object... args) {
        Object[] texts = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            texts[i] = String.valueOf(args[i]);
        }
        return MessageFormat.format(pattern.replace("'", "''"), texts);
    }
}
