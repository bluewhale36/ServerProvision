package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageRef;
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
 * {@link OSPackageRefRepository} 커스텀 쿼리 회귀 테스트.
 *
 * <p>H2 인메모리 DB + Hibernate 기반 DDL 로 실제 JPA 쿼리 동작을 검증한다.
 * 커버 대상:
 * <ul>
 *   <li>{@code existsByOsMetadata_IdAndName} — derived query 경로</li>
 *   <li>{@code countByOsMetadata_Id} — 인덱스 존재 판정 근거</li>
 *   <li>{@code deleteAllByOsMetadataId} — 재인덱싱 시 일괄 삭제</li>
 *   <li>{@code findExistingNames} — 차집합 계산을 위한 JPQL 쿼리</li>
 * </ul>
 * </p>
 */
@DataJpaTest
@ActiveProfiles("repo-test")
@Import(RepoTestObjectMapperConfig.class)
class OSPackageRefRepositoryTest {

    @Autowired private OSPackageRefRepository packageRefRepository;
    @Autowired private TestEntityManager entityManager;

    private OSMetadata meta1;
    private OSMetadata meta2;

    @BeforeEach
    void setUp() {
        // 서로 다른 OSMetadata 2개 — meta 간 격리 검증용
        meta1 = entityManager.persistAndFlush(OSMetadata.builder()
                .osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isoMountPath("/mnt/iso/rocky9").isEnabled(true).build());
        meta2 = entityManager.persistAndFlush(OSMetadata.builder()
                .osName(OSName.ROCKY_LINUX).osVersion("8.10")
                .isoMountPath("/mnt/iso/rocky8").isEnabled(true).build());

        // meta1: vim, httpd, nginx (이 중 nginx 만 meta2 에도 있다 — 동명 다른 OS)
        packageRefRepository.save(OSPackageRef.builder()
                .osMetadata(meta1).name("vim").repo("BaseOS").build());
        packageRefRepository.save(OSPackageRef.builder()
                .osMetadata(meta1).name("httpd").repo("AppStream").build());
        packageRefRepository.save(OSPackageRef.builder()
                .osMetadata(meta1).name("nginx").repo("AppStream").build());

        // meta2: nginx 만
        packageRefRepository.save(OSPackageRef.builder()
                .osMetadata(meta2).name("nginx").repo("AppStream").build());
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("existsByOsMetadata_IdAndName")
    class ExistsTest {

        @Test
        @DisplayName("해당 OS 에 존재하는 이름은 true 를 반환한다")
        void returnsTrue_whenPresent() {
            assertThat(packageRefRepository.existsByOsMetadata_IdAndName(meta1.getId(), "vim"))
                    .isTrue();
        }

        @Test
        @DisplayName("해당 OS 에 없는 이름은 false 를 반환한다")
        void returnsFalse_whenMissing() {
            assertThat(packageRefRepository.existsByOsMetadata_IdAndName(meta1.getId(), "notfound"))
                    .isFalse();
        }

        @Test
        @DisplayName("다른 OSMetadata 에만 존재하는 이름은 false — meta 간 격리 확인")
        void returnsFalse_whenOnlyInOtherMetadata() {
            // meta2 에 nginx 는 있지만 meta1.id 로 조회하면 meta1 에 있는 nginx 가 매칭됨.
            // 역으로 vim 은 meta1 에만 있고 meta2 조회 시 false 여야 한다.
            assertThat(packageRefRepository.existsByOsMetadata_IdAndName(meta2.getId(), "vim"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("countByOsMetadata_Id")
    class CountTest {

        @Test
        @DisplayName("해당 OS 의 패키지 수만 반환하며 타 OS 엔트리는 제외된다")
        void countsOnlyOwnMetadata() {
            assertThat(packageRefRepository.countByOsMetadata_Id(meta1.getId())).isEqualTo(3L);
            assertThat(packageRefRepository.countByOsMetadata_Id(meta2.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 OS ID 로 조회하면 0 을 반환한다")
        void returnsZero_whenNoEntries() {
            assertThat(packageRefRepository.countByOsMetadata_Id(99999L)).isZero();
        }
    }

    @Nested
    @DisplayName("deleteAllByOsMetadataId")
    class DeleteAllTest {

        @Test
        @DisplayName("대상 OS 의 엔트리만 삭제하며 타 OS 엔트리는 보존된다")
        void deletesOnlyOwnMetadata() {
            int deleted = packageRefRepository.deleteAllByOsMetadataId(meta1.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(deleted).isEqualTo(3);
            assertThat(packageRefRepository.countByOsMetadata_Id(meta1.getId())).isZero();
            // meta2 는 그대로
            assertThat(packageRefRepository.countByOsMetadata_Id(meta2.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("엔트리가 없는 OS ID 삭제는 0 을 반환한다")
        void returnsZero_whenNothingToDelete() {
            int deleted = packageRefRepository.deleteAllByOsMetadataId(99999L);
            assertThat(deleted).isZero();
        }
    }

    @Nested
    @DisplayName("findExistingNames — 차집합 계산용")
    class FindExistingNamesTest {

        @Test
        @DisplayName("입력 중 존재하는 이름만 반환 — 나머지는 '미존재' 로 차집합 계산 가능")
        void returnsOnlyExistingNames() {
            Set<String> existing = packageRefRepository.findExistingNames(
                    meta1.getId(), List.of("vim", "httpd", "typo-pkg", "unknown"));

            assertThat(existing).containsExactlyInAnyOrder("vim", "httpd");
        }

        @Test
        @DisplayName("빈 입력 컬렉션으로 조회하면 빈 Set 을 반환한다 (NOT 런타임 예외)")
        void returnsEmptySet_whenInputIsEmpty() {
            // Note: 일부 JPA 구현은 IN () 를 SQL 에러로 던지지만 Hibernate 는 빈 결과로 처리.
            Set<String> existing = packageRefRepository.findExistingNames(
                    meta1.getId(), List.of());
            assertThat(existing).isEmpty();
        }

        @Test
        @DisplayName("타 OSMetadata 에 있는 이름은 포함되지 않는다 — meta 스코프 확인")
        void scopedByOsMetadata() {
            // meta2 에는 nginx 만 있으므로 vim/httpd 조회 시 빈 결과여야 한다.
            Set<String> existing = packageRefRepository.findExistingNames(
                    meta2.getId(), List.of("vim", "httpd"));
            assertThat(existing).isEmpty();

            // 반면 nginx 는 meta2 에 존재.
            Set<String> existingNginx = packageRefRepository.findExistingNames(
                    meta2.getId(), List.of("nginx"));
            assertThat(existingNginx).containsExactly("nginx");
        }
    }
}
