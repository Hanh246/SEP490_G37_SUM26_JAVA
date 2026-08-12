package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IForumCategoryRepository extends JpaRepository<ForumCategoryEntity, UUID> {

    List<ForumCategoryEntity> findByIsActiveTrueAndDeletedFalseOrderByNameAsc();

    Optional<ForumCategoryEntity> findByIdAndDeletedFalse(UUID id);

    Optional<ForumCategoryEntity> findByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIdNotAndDeletedFalse(String name, UUID id);
}
