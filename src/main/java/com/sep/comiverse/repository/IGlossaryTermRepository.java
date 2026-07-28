package com.sep.comiverse.repository;

import com.sep.comiverse.entity.GlossaryTermEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IGlossaryTermRepository extends JpaRepository<GlossaryTermEntity, UUID> {

    List<GlossaryTermEntity> findByComicIdOrderByCreatedAtDesc(UUID comicId);
}