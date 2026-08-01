package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IChapterTranslationRepository extends JpaRepository<ChapterTranslationEntity, UUID> {

    Optional<ChapterTranslationEntity> findByChapter_IdAndLanguageCode(UUID chapterId, String languageCode);

    List<ChapterTranslationEntity> findByChapter_Id(UUID chapterId);

    @Query("SELECT DISTINCT ct.languageCode FROM ChapterTranslationEntity ct WHERE ct.chapter.comic.id = :comicId")
    List<String> findDistinctLanguageCodesByComicId(@Param("comicId") UUID comicId);

    @Query(value = """
            SELECT COUNT(DISTINCT ct.id)
            FROM chapter_translations ct
            JOIN team_members tm ON tm.team_id = ct.project_team_id
            WHERE tm.user_id = :userId
              AND COALESCE(ct.deleted, false) = false
              AND ct.create_at >= :fromInclusive
              AND ct.create_at < :toExclusive
            """, nativeQuery = true)
    long countTranslationsForUserInPeriod(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

}