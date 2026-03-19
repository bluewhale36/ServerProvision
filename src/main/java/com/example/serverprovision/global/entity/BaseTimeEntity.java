package com.example.serverprovision.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt; // 엔티티가 생성되어 저장될 때 시간이 자동 저장됨

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 조회한 엔티티의 값을 변경할 때 시간이 자동 저장됨
}
