package com.sep.comiverse.repository;


import com.sep.comiverse.entity.PageTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IPageTranslationRepository extends JpaRepository<PageTranslationEntity, UUID> {
    List<PageTranslationEntity> findByTaskId_IdOrderByPageNumberAsc(UUID taskId);

}
