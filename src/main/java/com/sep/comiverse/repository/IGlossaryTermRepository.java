package com.sep.comiverse.repository;

import com.sep.comiverse.entity.GlossaryTermEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IGlossaryTermRepository extends JpaRepository<GlossaryTermEntity, UUID> {

    @Query("SELECT g FROM GlossaryTermEntity g WHERE g.comicId = :comicId OR g.projectId = :comicId ORDER BY g.createdAt DESC")
    List<GlossaryTermEntity> findByComicIdOrderByCreatedAtDesc(@Param("comicId") UUID comicId);
}