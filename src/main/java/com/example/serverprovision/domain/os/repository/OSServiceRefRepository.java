package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSServiceRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface OSServiceRefRepository extends JpaRepository<OSServiceRef, Long> {

    boolean existsByOsMetadata_IdAndName(Long osMetadataId, String name);

    long countByOsMetadata_Id(Long osMetadataId);

    @Modifying
    @Query("delete from OSServiceRef s where s.osMetadata.id = :osMetadataId")
    int deleteAllByOsMetadataId(@Param("osMetadataId") Long osMetadataId);

    @Query("select s.name from OSServiceRef s " +
            "where s.osMetadata.id = :osMetadataId and s.name in :names")
    Set<String> findExistingNames(@Param("osMetadataId") Long osMetadataId,
                                  @Param("names") Collection<String> names);
}
