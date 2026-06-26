package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.GuestServerCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestServerCustomRepository extends JpaRepository<GuestServerCustom, UUID> {

    Optional<GuestServerCustom> findByGuestServer_Id(UUID guestServerId);

    /**
     * 인라인 수정 — 사내 시리얼 번호(유니크) 중복 검사. 같은 서버는 제외.
     */
    boolean existsByProductSerialNumberAndGuestServer_IdNot(String productSerialNumber, UUID guestServerId);
}
