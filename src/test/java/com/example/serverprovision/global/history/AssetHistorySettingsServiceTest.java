package com.example.serverprovision.global.history;

import com.example.serverprovision.global.history.dto.response.AssetHistorySettingsResponse;
import com.example.serverprovision.global.history.entity.AssetHistorySettings;
import com.example.serverprovision.global.history.repository.AssetHistorySettingsRepository;
import com.example.serverprovision.global.history.service.internal.AssetHistorySettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * E1-I-2-b-2 CP4 — 보존 개수 설정(singleton 시드 + 캐시) 검증. mock 리포지토리로 시드·정리·갱신 로직을 확인하고,
 * 실 DB 매핑은 샌드박스(CP5)로 확인한다({@code TrashSettingsService} 미러).
 */
class AssetHistorySettingsServiceTest {

    private final AssetHistorySettingsRepository repository = mock(AssetHistorySettingsRepository.class);
    private AssetHistorySettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        AssetHistoryProperties properties = new AssetHistoryProperties();
        ReflectionTestUtils.setField(properties, "root", "/tmp/asset-history");
        ReflectionTestUtils.setField(properties, "retentionCount", 3);   // property = 시드 기본값
        service = new AssetHistorySettingsServiceImpl(repository, properties);
    }

    @Test
    @DisplayName("시드 — 행 없으면 property 기본값(3)으로 생성")
    void seed_createsFromProperty() {
        given(repository.count()).willReturn(0L);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.seedOrReconcile();

        ArgumentCaptor<AssetHistorySettings> captor = ArgumentCaptor.forClass(AssetHistorySettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRetentionCount()).isEqualTo(3);
        assertThat(service.getRetentionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("시드 — 행 1개면 그대로 신뢰(DB 값이 property 오버라이드)")
    void seed_existingRowWins() {
        given(repository.count()).willReturn(1L);
        given(repository.findFirstByOrderByIdAsc()).willReturn(Optional.of(AssetHistorySettings.seed(5)));

        service.seedOrReconcile();

        assertThat(service.getRetentionCount()).isEqualTo(5);   // property(3) 아닌 DB(5)
    }

    @Test
    @DisplayName("update — 보존 개수 교체 + 캐시 갱신")
    void update_changesRetention() {
        given(repository.count()).willReturn(1L);
        given(repository.findFirstByOrderByIdAsc()).willReturn(Optional.of(AssetHistorySettings.seed(3)));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
        service.seedOrReconcile();

        AssetHistorySettingsResponse response = service.update(7);

        assertThat(response.retentionCount()).isEqualTo(7);
        assertThat(service.getRetentionCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("정리 — 행 2개 이상이면 첫 행만 신뢰 + 나머지 삭제")
    void reconcile_multipleRows() {
        AssetHistorySettings first = AssetHistorySettings.seed(3);
        ReflectionTestUtils.setField(first, "id", 1L);
        AssetHistorySettings dup = AssetHistorySettings.seed(9);
        ReflectionTestUtils.setField(dup, "id", 2L);
        given(repository.count()).willReturn(2L);
        given(repository.findFirstByOrderByIdAsc()).willReturn(Optional.of(first));
        given(repository.findAll()).willReturn(List.of(first, dup));

        service.seedOrReconcile();

        verify(repository).delete(dup);
        assertThat(service.getRetentionCount()).isEqualTo(3);
    }
}
