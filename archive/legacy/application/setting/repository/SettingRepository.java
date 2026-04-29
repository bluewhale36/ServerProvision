package com.example.serverprovision.application.setting.repository;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ServerSetting} 엔티티의 CRUD를 담당하는 Spring Data JPA Repository이다.
 *
 * <p>역할: {@link ServerSetting} 엔티티의 기본적인 저장·조회·삭제 기능을 제공한다.
 * {@link JpaRepository}가 제공하는 기본 메소드 외에 현재 추가된 커스텀 쿼리 메소드는 없다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * {@code settingRepository.save(setting)}으로 새 세팅 주문서를 영속화할 때 사용된다.
 * 향후 세팅 주문서 조회 기능 구현 시 이 Repository에 쿼리 메소드를 추가한다.</p>
 *
 * <p>확장 가이드: {@code GET /pxe/v1/setting/{id}} 조회 엔드포인트 구현 시
 * {@link JpaRepository#findById}를 직접 사용하면 된다.
 * 이름 또는 상태 기반 목록 조회가 필요하면
 * {@code findByName(String name)},
 * {@code findByStatus(SettingStatus status)} 등의 메소드를 이 인터페이스에 선언한다.</p>
 */
@Repository
public interface SettingRepository extends JpaRepository<ServerSetting, Long> {

    /**
     * 수정 전용 비관적 쓰기 락으로 세팅 주문서를 조회한다.
     *
     * <p>역할: {@link LockModeType#PESSIMISTIC_WRITE}를 획득하여 조회와 상태 검증 사이에
     * 다른 트랜잭션이 {@code status}를 변경하는 race condition을 차단한다.</p>
     *
     * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#update}에서만
     * 사용된다. 읽기 전용 경로({@code findById})는 이 메서드를 사용하지 않는다.</p>
     *
     * @param id 조회할 세팅 주문서의 PK
     * @return 락이 걸린 {@link ServerSetting}을 감싼 {@link Optional}. 없으면 empty.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServerSetting s WHERE s.id = :id")
    Optional<ServerSetting> findByIdForUpdate(@Param("id") Long id);
}
