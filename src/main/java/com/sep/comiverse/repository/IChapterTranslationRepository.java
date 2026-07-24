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
}