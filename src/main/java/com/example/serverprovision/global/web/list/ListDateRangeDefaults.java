package com.example.serverprovision.global.web.list;

import com.example.serverprovision.global.web.list.vo.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 목록 기간 조건의 '오늘' 과 기본 범위를 컨트롤러에 공급한다(S8-5). 세 목록 컨트롤러가 같은 세 줄
 * (Clock 으로 오늘 · 기본 시작일 · 모델 싣기)을 복제하지 않게 한 곳에 둔다.
 * 모델 이름 {@code rangeDefaultFrom} · {@code rangeDefaultTo} 는 조각 {@code fragments/ui/date-range-row} 가 읽는다.
 */
@Component
@RequiredArgsConstructor
public class ListDateRangeDefaults {

    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 오늘 기준 기본 범위를 모델에 싣고 그 오늘을 돌려준다. */
    public LocalDate addTo(Model model) {
        LocalDate today = today();
        model.addAttribute("rangeDefaultFrom", DateRange.defaultFrom(today));
        model.addAttribute("rangeDefaultTo", today);
        model.addAttribute("rangeToday", today);
        return today;
    }
}
