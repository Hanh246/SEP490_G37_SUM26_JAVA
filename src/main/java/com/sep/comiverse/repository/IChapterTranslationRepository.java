package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IChapterTranslationRepository extends JpaRepository<ChapterTranslationEntity, UUID> {

    Optional<ChapterTranslationEntity> findByChapter_IdAndLanguageCode(UUID chapterId, String languageCode);

    List<ChapterTranslationEntity> findByChapter_Id(UUID chapterId);

    @Query("SELECT DISTINCT ct.languageCode FROM ChapterTranslationEntity ct WHERE ct.chapter.comic.id = :comicId")
    List<String> findDistinctLanguageCodesByComicId(@Param("comicId") UUID comicId);

    @Query("SELECT ct.chapter.id, ct.languageCode FROM ChapterTranslationEntity ct WHERE ct.chapter.comic.id = :comicId")
    List<Object[]> findLanguageCodesByChapterForComic(@Param("comicId") UUID comicId);

    @Query("SELECT ct FROM ChapterTranslationEntity ct " +
            "LEFT JOIN FETCH ct.chapter c " +
            "LEFT JOIN FETCH c.comic " +
            "WHERE ct.id = :id AND (ct.deleted = false OR ct.deleted IS NULL)")
    Optional<ChapterTranslationEntity> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT ct.id FROM ChapterTranslationEntity ct WHERE ct.projectTeamId IN (" +
            "SELECT pt.id FROM ProjectTeamEntity pt WHERE pt.leaderId = :leaderId AND (pt.deleted = false OR pt.deleted IS NULL)" +
            ") AND (ct.deleted = false OR ct.deleted IS NULL)")
    List<UUID> findTranslationIdsByLeaderId(@Param("leaderId") UUID leaderId);

    @Query("SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END FROM ChapterTranslationEntity ct, ProjectTeamEntity pt " +
            "WHERE ct.id = :translationId AND ct.projectTeamId = pt.id AND pt.leaderId = :userId " +
            "AND (ct.deleted = false OR ct.deleted IS NULL) AND (pt.deleted = false OR pt.deleted IS NULL)")
    boolean isUserLeaderOfTranslation(@Param("translationId") UUID translationId, @Param("userId") UUID userId);
}