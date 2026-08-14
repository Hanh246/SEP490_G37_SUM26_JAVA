package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITranslatorRepository extends JpaRepository<TranslatorEntity, UUID> {
    boolean existsByUser_Id(UUID id);
    Optional<TranslatorEntity> findByUser_Id(UUID id);
    List<TranslatorEntity> findAllByUser_IdIn(Collection<UUID> userIds);
}
