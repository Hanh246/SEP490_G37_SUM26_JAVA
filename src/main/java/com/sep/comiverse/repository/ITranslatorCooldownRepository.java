package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorCooldownEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITranslatorCooldownRepository extends JpaRepository<TranslatorCooldownEntity, UUID> {

    /** Find any active (not yet expired) cooldown for a user */
    @Query("SELECT c FROM TranslatorCooldownEntity c WHERE c.userId = :userId AND c.cooldownUntil > :now")
    List<TranslatorCooldownEntity> findActiveCooldowns(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT c FROM TranslatorCooldownEntity c WHERE c.userId = :userId AND c.relatedTeamId = :teamId AND c.cooldownUntil > :now")
    List<TranslatorCooldownEntity> findActiveCooldownsForTeam(
            @Param("userId") UUID userId,
            @Param("teamId") UUID teamId,
            @Param("now") Instant now
    );

    /** Clean up expired cooldowns */
    @Query("DELETE FROM TranslatorCooldownEntity c WHERE c.cooldownUntil < :now")
    void deleteExpiredCooldowns(@Param("now") Instant now);
}
