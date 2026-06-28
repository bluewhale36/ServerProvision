package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.GuestServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GuestServerRepository extends JpaRepository<GuestServer, UUID> {

    /**
     * 재부팅 멱등성 — 동일 SMBIOS UUID 의 서버가 이미 등록되어 있는지 검사.
     * PXE 클라이언트는 매 부팅마다 /boot 를 호출하므로, 중복 등록을 사전 차단한다.
     */
    boolean existsBySystemUUID(UUID systemUUID);

    /**
     * 목록 조회 — 최근 등록 순.
     */
    List<GuestServer> findAllByOrderByCreatedAtDesc();

    /**
     * 인라인 수정 — 이름(유니크) 중복 검사. 자기 자신은 제외.
     */
    boolean existsByNameAndIdNot(String name, UUID id);

    /**
     * 인라인 수정 — 사내 시리얼 번호(유니크) 중복 검사. 자기 자신은 제외. (U1 §D1: serial 이 guest_server 로 흡수됨)
     */
    boolean existsBySerialNumberAndIdNot(String serialNumber, UUID id);
}
