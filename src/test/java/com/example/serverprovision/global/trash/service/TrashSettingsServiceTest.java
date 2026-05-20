package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.trash.entity.TrashSettings;
import com.example.serverprovision.global.trash.repository.TrashSettingsRepository;
import com.example.serverprovision.global.trash.service.internal.TrashSettingsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * S5-2-4 — TrashSettingsService 본체의 singleton 보장 패턴 (count==0/1/&gt;=2) 검증.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrashSettingsServiceTest {

    @Mock TrashSettingsRepository repository;

    @InjectMocks TrashSettingsServiceImpl service;

    @Test
    @DisplayName("(8) seedOrReconcile : count==0 — default 시드 insert + 캐시 보유")
    void seed_whenEmpty() {
        given(repository.count()).willReturn(0L);
        TrashSettings seeded = TrashSettings.defaults();
        given(repository.save(any(TrashSettings.class))).willReturn(seeded);

        service.seedOrReconcile();

        verify(repository, times(1)).save(any(TrashSettings.class));
        assertThat(service.getTtl().toDays()).isEqualTo(30);  // 캐시 동작
    }

    @Test
    @DisplayName("(9) seedOrReconcile : count==1 — save 호출 0회 + 첫 row 그대로 캐시")
    void singleton_whenOne() {
        given(repository.count()).willReturn(1L);
        TrashSettings existing = TrashSettings.defaults();
        given(repository.findFirstByOrderByIdAsc()).willReturn(Optional.of(existing));

        service.seedOrReconcile();

        verify(repository, never()).save(any(TrashSettings.class));
        assertThat(service.isAutoPurgeEnabled()).isTrue();
    }

    @Test
    @DisplayName("(10) seedOrReconcile : count>=2 — 첫 row 만 신뢰 + 나머지 hard-delete")
    void reconcile_whenManyRows() {
        given(repository.count()).willReturn(3L);
        // id 비교를 위해 mock 으로 id 명시.
        TrashSettings first = mock(TrashSettings.class);
        TrashSettings dupA  = mock(TrashSettings.class);
        TrashSettings dupB  = mock(TrashSettings.class);
        given(first.getId()).willReturn(1L);
        given(dupA.getId()).willReturn(2L);
        given(dupB.getId()).willReturn(3L);
        // currentEntity() 가 사용하는 getter 도 stub (캐시 동작 검증 우회)
        given(first.getRetryMaxAttempts()).willReturn(3);
        given(repository.findFirstByOrderByIdAsc()).willReturn(Optional.of(first));
        given(repository.findAll()).willReturn(List.of(first, dupA, dupB));

        service.seedOrReconcile();

        // 첫 row 외 2건 hard-delete
        verify(repository, times(2)).delete(any(TrashSettings.class));
        assertThat(service.getRetryMaxAttempts()).isEqualTo(3);
    }
}
