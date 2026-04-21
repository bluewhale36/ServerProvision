package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSServiceRef;
import com.example.serverprovision.domain.os.model.enums.OSName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OSServiceRefRepository} 커스텀 쿼리 회귀 테스트.
 *
 * <p>{@link OSPackageRefRepository} 와 동일 시그니처이며, {@code providedByPkg} 추가 컬럼이 있다.
 * 여기서는 메서드 시그니처가 동일하므로 동일 시나리오를 확인한다.</p>
 */
@DataJpaTest
@ActiveProfiles("repo-test")
@Import(RepoTestObjectMapperConfig.class)
class OSServiceRefRepositoryTest {

    @Autowired private OSServiceRefRepository serviceRefRepository;
    @Autowired private TestEntityManager entityManager;

    private OSMetadata meta1;
    private OSMetadata meta2;

    @BeforeEach
    void setUp() {
        meta1 = entityManager.persistAndFlush(OSMetadata.builder()
                .osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isoMountPath("/mnt/iso/rocky9").isEnabled(true).build());
        meta2 = entityManager.persistAndFlush(OSMetadata.builder()
                .osName(OSName.CENTOS).osVersion("7.9")
                .isoMountPath("/mnt/iso/centos7").isEnabled(true).build());

        // meta1: httpd, sshd, chronyd
        serviceRefRepository.save(OSServiceRef.builder()
                .osMetadata(meta1).name("httpd").providedByPkg("httpd").build());
        serviceRefRepository.save(OSServiceRef.builder()
                .osMetadata(meta1).name("sshd").providedByPkg("openssh-server").build());
        serviceRefRepository.save(OSServiceRef.builder()
                .osMetadata(meta1).name("chronyd").providedByPkg("chrony").build());

        // meta2: httpd (동명 별도 OS 에도 존재 — 스코프 격리용)
        serviceRefRepository.save(OSServiceRef.builder()
                .osMetadata(meta2).name("httpd").providedByPkg("httpd").build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("existsByOsMetadata_IdAndName — 해당 OS 에 존재하는 unit 만 true")
    void existsPerMetadata() {
        assertThat(serviceRefRepository.existsByOsMetadata_IdAndName(meta1.getId(), "sshd"))
                .isTrue();
        assertThat(serviceRefRepository.existsByOsMetadata_IdAndName(meta2.getId(), "sshd"))
                .isFalse();
    }

    @Test
    @DisplayName("countByOsMetadata_Id — 대상 OS 의 서비스 개수만 반환")
    void countPerMetadata() {
        assertThat(serviceRefRepository.countByOsMetadata_Id(meta1.getId())).isEqualTo(3L);
        assertThat(serviceRefRepository.countByOsMetadata_Id(meta2.getId())).isEqualTo(1L);
    }

    @Nested
    @DisplayName("deleteAllByOsMetadataId")
    class DeleteAllTest {

        @Test
        @DisplayName("대상 OS 엔트리만 삭제, 타 OS 는 보존")
        void deletesOnlyOwnMetadata() {
            int deleted = serviceRefRepository.deleteAllByOsMetadataId(meta1.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(deleted).isEqualTo(3);
            assertThat(serviceRefRepository.countByOsMetadata_Id(meta1.getId())).isZero();
            assertThat(serviceRefRepository.countByOsMetadata_Id(meta2.getId())).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("findExistingNames — 서비스 이름 차집합")
    class FindExistingNamesTest {

        @Test
        @DisplayName("입력 이름 중 존재하는 것만 반환한다")
        void returnsOnlyExisting() {
            Set<String> existing = serviceRefRepository.findExistingNames(
                    meta1.getId(), List.of("httpd", "sshd", "ngix", "mysqld"));
            assertThat(existing).containsExactlyInAnyOrder("httpd", "sshd");
        }

        @Test
        @DisplayName("meta 격리 — meta2 의 http d 는 meta2 스코프에서만 매칭된다")
        void scopedByOsMetadata() {
            Set<String> onMeta1 = serviceRefRepository.findExistingNames(
                    meta1.getId(), List.of("httpd"));
            Set<String> onMeta2 = serviceRefRepository.findExistingNames(
                    meta2.getId(), List.of("httpd"));
            assertThat(onMeta1).containsExactly("httpd");
            assertThat(onMeta2).containsExactly("httpd");

            // sshd 는 meta1 에만 존재
            assertThat(serviceRefRepository.findExistingNames(meta2.getId(), List.of("sshd")))
                    .isEmpty();
        }
    }
}
