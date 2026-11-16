package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** HF26 — 메인보드 영구 삭제를 막는 서버 하드웨어 기록. */
@ExtendWith(MockitoExtension.class)
class GuestServerBoardReferenceTest {

    @Mock GuestServerDetailRepository repository;
    @InjectMocks GuestServerBoardReference reference;

    @Test
    @DisplayName("그 보드로 등록된 서버가 있으면 대수를 적고, 없으면 null")
    void countsServers() {
        given(repository.countByBoardModel_Id(3L)).willReturn(2L);
        given(repository.countByBoardModel_Id(4L)).willReturn(0L);

        assertThat(reference.remainingReference(3L)).isEqualTo("등록된 서버 2 대");
        assertThat(reference.remainingReference(4L)).isNull();
    }
}
