package com.example.serverprovision.provisioning.group.web;

import com.example.serverprovision.provisioning.group.vo.GroupFilter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * 파라미터 {@code group} → {@link GroupFilter}(S8-2). Spring Boot 가 {@code Converter} 빈을 MVC 변환에 자동 등록하므로 별도 설정 클래스는 두지 않는다. "none" 은 무소속, 숫자는 그룹 id 다. 그 밖의 값은
 * {@code NumberFormatException} 이 바인딩 실패로 이어져 400 이 된다(알 수 없는 enum 값과 같은 경로).
 * 빈 문자열("그룹 전체")은 {@link GroupFilter#ANY} 다. 파라미터가 아예 없으면 컨트롤러가 null 을 ANY 로 받는다.
 */
@Component
public class GroupFilterConverter implements Converter<String, GroupFilter> {

    @Override
    public GroupFilter convert(String source) {
        String value = source.trim();
        if (value.isEmpty()) {
            return GroupFilter.ANY;
        }
        if (GroupFilter.UNGROUPED_PARAM.equals(value)) {
            return GroupFilter.UNGROUPED;
        }
        return GroupFilter.of(Long.parseLong(value));
    }
}
