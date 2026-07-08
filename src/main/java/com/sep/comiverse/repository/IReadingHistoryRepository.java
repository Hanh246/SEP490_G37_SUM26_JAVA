package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReadingHistoryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IReadingHistoryRepository extends AbstractCrudRepository<ReadingHistoryEntity, UUID> {
    
    boolean existsByChapterIdAndUserId(UUID chapterId, UUID userId);
    
    Optional<ReadingHistoryEntity> findByChapterIdAndUserId(UUID chapterId, UUID userId);
    
    @Query("SELECT rh.chapterId FROM ReadingHistoryEntity rh WHERE rh.userId = :userId AND rh.comicId = :comicId AND rh.deleted = false")
    List<UUID> findReadChapterIdsByUserIdAndComicId(@Param("userId") UUID userId, @Param("comicId") UUID comicId);
    
    @Query("SELECT rh.comicId FROM ReadingHistoryEntity rh WHERE rh.userId = :userId AND rh.deleted = false GROUP BY rh.comicId ORDER BY MAX(rh.updatedAt) DESC")
    List<UUID> findReadComicIdsByUserId(@Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ReadingHistoryEntity rh WHERE rh.comicId = :comicId AND rh.userId = :userId")
    void deleteByComicIdAndUserId(@Param("comicId") UUID comicId, @Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM reading_histories WHERE update_at < :oneMonthAgo AND id NOT IN (SELECT id FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, comic_id ORDER BY update_at DESC) as rn FROM reading_histories) t WHERE t.rn = 1)", nativeQuery = true)
    void deleteOldHistoryExceptLatest(@Param("oneMonthAgo") java.time.Instant oneMonthAgo);
}
