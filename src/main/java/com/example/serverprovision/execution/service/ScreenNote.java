package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.setting.BmcSettingItem;

import com.example.serverprovision.global.exception.UserFacingMessage;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 원장 사유를 화면에 그리기 직전에 다듬는다(S22-3). 원장에는 원인 추적용으로 BMC 요청 경로("PUT /api/settings/…")와
 * 항목 상수("DATE_TIME")가 함께 적히는데, 운영자가 그 자리에서 알아야 할 것은 무엇이 왜 안 됐는가다. 원문은 원장과
 * 로그에 그대로 두고, 화면으로 나가는 문장만 바꾼다 — 요청 경로는 빼고, 항목 상수는 항목의 화면 이름으로 바꾼다.
 */
final class ScreenNote {

    /** "METHOD /경로 — " 덩어리. 사유 문장 앞이나 항목 이름 뒤에 끼어 있다. */
    private static final Pattern REQUEST = Pattern.compile("(?:GET|POST|PUT|PATCH|DELETE) /\\S*\\s+—\\s+");
    private static final Pattern ITEM = Pattern.compile("\\b(" + Arrays.stream(BmcSettingItem.values())
            .map(Enum::name).collect(Collectors.joining("|")) + ")\\b");

    /**
     * 원장에 저장된 엔진 · 에이전트 문장 → 화면 문장(S22-3). 저장된 문장은 출처를 고쳐도 과거 행에 남으므로 여기서
     * 옛 문장과 지금 문장을 함께 받는다. 위에서부터 차례로 적용한다(구체적인 것이 먼저).
     */
    private static final List<Rewrite> REWRITES = List.of(
            // 에이전트(진단 리눅스) 영어 원문 — RAID 카드 탐지 · 관리 도구
            new Rewrite("no raid-class pci device", "RAID 카드(PCI)를 찾지 못했습니다"),
            new Rewrite("no family driver bound \\(server hint: ([^)]*)\\)", "RAID 카드 드라이버가 올라오지 않았습니다(기대한 드라이버: $1)"),
            new Rewrite("storcli64/storcli not found", "진단 이미지에 RAID 관리 도구(storcli)가 없습니다"),
            new Rewrite("sas3ircu not found", "진단 이미지에 RAID 관리 도구(sas3ircu)가 없습니다"),
            new Rewrite("unknown family (\\S+) \\(agent adapter missing\\)", "에이전트가 이 RAID 계열($1)을 다루지 못합니다"),
            new Rewrite("sas3ircu does not list controller 0 \\(IT firmware\\?\\)", "RAID 카드가 IT 펌웨어라 RAID 를 구성할 수 없습니다"),
            new Rewrite("VD lookup by name (\\S+)", "만든 볼륨($1)을 찾지 못했습니다"),
            new Rewrite("^(?:\\S*/)?(?:storcli64|storcli|sas3ircu)\\s.*$", "RAID 관리 명령이 실패했습니다"),
            // 진단 수집
            new Rewrite("boardSerial\\(duplicate\\)=(\\S+)", "보드 시리얼($1)이 다른 서버와 겹쳐 이 서버에서는 쓰지 않습니다"),
            new Rewrite("raid\\(unparsable\\)=", "RAID 정보를 해석하지 못했습니다: "),
            new Rewrite("raid\\([\\w\\-]+\\)=", "RAID 정보를 모으지 못했습니다: "),
            // 다음 부팅 지정(네트워크 부팅) 관찰 접두
            new Rewrite("다음 부팅 \\S+ 강제 : 반영 확인 · ", ""),
            new Rewrite("다음 부팅 \\S+ 강제 : 미확인\\(pending 경유 가능\\) · ", "네트워크 부팅 지정을 확인하지 못했습니다. "),
            new Rewrite("다음 부팅 \\S+ 강제 : 거절\\((.*?)\\) — 부트 순서대로 부팅될 수 있습니다 · ", "BMC 가 네트워크 부팅 지정을 거절했습니다($1). "),
            // 전원 명령
            new Rewrite("(켜기|재시작|강제 끄기|정상 종료)\\((?:On|ForceRestart|ForceOff|GracefulShutdown|PowerCycle)\\) 발행 — 게스트가 PXE 로 돌아오면 이어서 진행합니다\\.",
                    "$1 명령을 보냈습니다."),
            new Rewrite("(켜기|재시작|강제 끄기|정상 종료)\\((?:On|ForceRestart|ForceOff|GracefulShutdown|PowerCycle)\\) 명령이 전달되었습니다 — \\[상태 조회\\] 로 결과를 확인하세요\\.",
                    "$1 명령을 보냈습니다."),
            new Rewrite("전원 차단\\(ForceOff\\) — 굽는 동안 꺼진 채 유지됩니다", "전원을 껐습니다. 굽는 동안 꺼진 채로 둡니다"),
            new Rewrite("전원이 켜졌습니다 \\(Reset\\(On\\) → PowerState 폴링 확인\\)\\.", "전원을 켰습니다."),
            new Rewrite("PowerCycle 폴백으로 전원이 켜졌습니다\\.", "전원을 껐다 켜서 켰습니다."),
            // 펌웨어 굽기 결과
            new Rewrite("^전송 완료$", "굽기 완료"),
            new Rewrite("^목표 (\\S+) · 확인 (.+)$", "구운 뒤 다시 읽은 버전이 목표와 다릅니다(목표 $1, 읽은 값 $2)."),
            new Rewrite("^시한 PT\\S+ 초과$", "펌웨어 업데이트가 실패했습니다."),
            new Rewrite("^BMC 가 굽기를 실패로 종결했습니다$", "펌웨어 업데이트가 실패했습니다."),
            // Windows 설치
            new Rewrite("서빙 후 \\d+분 — 설치 시한 (\\d+)분 초과", "설치 시한($1분)을 넘겼습니다"),
            new Rewrite("재진입 \\d+회 — 상한 (\\d+)회 초과", "재부팅이 상한($1회)을 넘겨 반복됐습니다"),
            new Rewrite("새 서빙으로 대체 — 열린 채 남아 있던 행", "새 설치 시도로 대체됐습니다"),
            // BIOS 설정 레지스트리 대조(옛 문장)
            new Rewrite("BIOS (\\S+) 레지스트리 허용값 밖 : ", "BIOS 설정 템플릿이 이 보드의 BIOS($1)와 맞지 않습니다: "),
            new Rewrite(" — 레지스트리에 없는 속성", "(이 BIOS 에 없는 항목)"),
            new Rewrite(" — 허용 \\{([^}]*)\\}", "(허용 값: $1)"),
            // 자원 대기 시한 실패(detail 이 없던 옛 행)
            new Rewrite("^hold-ttl$", "자원 대기 시한이 지나 실패했습니다. 자원을 되살린 뒤 재시도해야 합니다."),
            // '~음' 꼴 표기(S22-3 표기 규칙)
            // 펌웨어 판정 사유(옛 문장 — 출처는 S22-3 에서 고쳤다)
            new Rewrite("정의서가 지정한 펌웨어가 더 이상 없어 이 축을 건너뜁니다", "정의서가 지정한 펌웨어가 삭제돼 건너뜁니다"),
            new Rewrite("이 보드에 사용할 수 있는 펌웨어가 없어 이 축을 건너뜁니다", "이 보드에 쓸 수 있는 펌웨어가 없어 건너뜁니다"),
            new Rewrite("이 축을 건너뜁니다", "건너뜁니다"),
            new Rewrite("펌웨어의 무결성 표식이 없습니다", "펌웨어 파일의 무결성 표식이 없어 쓸 수 없습니다"),
            new Rewrite("펌웨어의 무결성 표식이 유효하지 않습니다", "펌웨어 파일이 변조됐거나 손상돼 쓸 수 없습니다(무결성 표식 불일치)"),
            new Rewrite("CSRFToken 없음", "CSRFToken 미수신"),
            new Rewrite(" 건너뜀", " 생략")
    );

    private record Rewrite(Pattern pattern, String replacement) {
        Rewrite(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }

        String apply(String text) {
            return pattern.matcher(text).replaceAll(replacement);
        }
    }

    private ScreenNote() {
    }

    static String of(String note) {
        if (note == null) {
            return null;
        }
        String rewritten = note;
        for (Rewrite rewrite : REWRITES) {
            rewritten = rewrite.apply(rewritten);
        }
        String withoutRequest = REQUEST.matcher(rewritten).replaceAll("");
        Matcher m = ITEM.matcher(withoutRequest);
        String named = m.replaceAll(r -> Matcher.quoteReplacement(BmcSettingItem.valueOf(r.group(1)).getDescription()));
        return UserFacingMessage.punctuate(named);
    }
}
