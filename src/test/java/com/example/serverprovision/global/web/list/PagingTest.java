package com.example.serverprovision.global.web.list;

import com.example.serverprovision.provisioning.setting.enums.SettingSortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/** 정렬 화이트리스트의 한 자리 — 리졸버가 만든 Sort 는 버리고 enum 의 속성으로 다시 만든다(S8-1 D4 · D5). */
class PagingTest {

    @Test
    @DisplayName("리졸버가 원시 속성명으로 만든 Sort 는 버리고 page · size 만 가져온다")
    void discardsResolverSort() {
        Pageable fromResolver = PageRequest.of(2, 50, Sort.by("guestToken"));

        PageRequest result = Paging.of(fromResolver, SettingSortField.NAME, Sort.Direction.DESC);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(50);
        // 같은 이름끼리는 id 로 한 번 더 줄 세운다 — 쪽 경계에서 행이 겹치거나 빠지지 않게
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "name").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    @DisplayName("쪽 정보가 없는 Pageable 이면 첫 쪽 · 기본 표시 개수")
    void unpagedFallsBackToDefaults() {
        PageRequest result = Paging.of(Pageable.unpaged(), SettingSortField.ID, Sort.Direction.ASC);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(Paging.DEFAULT_SIZE);
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }
}
