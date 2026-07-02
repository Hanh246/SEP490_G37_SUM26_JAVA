package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Component
public class ChapterCrudPlugin
        extends AbstractCrudPlugin<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final IChapterRepository chapterRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CHAPTER_CACHE_PREFIX = "chapter:detail:";

    private static final String COMIC_VIEW_HASH = "comic:view:counter";
    private static final String CHAPTER_VIEW_HASH = "chapter:view:counter";

    @Autowired
    public ChapterCrudPlugin(IChapterRepository repository,
                             PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                             RedisTemplate<String, Object> redisTemplate){
        super(repository, pluginRegistry, ChapterEntity.class);
        this.chapterRepository = repository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public ChapterDTO getChapterDetail(UUID chapterId, UUID userId) {
        String cacheKey = CHAPTER_CACHE_PREFIX + chapterId.toString();

        ChapterDTO dto = (ChapterDTO) redisTemplate.opsForValue().get(cacheKey);

        if (dto == null) {
            ChapterEntity entity = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new RuntimeException("Chapter not found"));

            dto = plugin.toDto(entity);

            redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofDays(3));
        }

        trackAndIncrementView(dto.getComicId(), chapterId, userId);

        Integer redisChapterViews = (Integer) redisTemplate.opsForHash().get(CHAPTER_VIEW_HASH, chapterId.toString());
        if (redisChapterViews != null) {
            dto.setViewCount(dto.getViewCount() + redisChapterViews);
        }

        return dto;
    }

    private void trackAndIncrementView(UUID comicId, UUID chapterId, UUID userId) {
        String userIdentity = (userId != null) ? userId.toString() : "anonymous";
        String lockKey = String.format("view:lock:user:%s:chapter:%s", userIdentity, chapterId);

        Boolean isFirstTimeIn10Mins = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isFirstTimeIn10Mins)) {
            redisTemplate.opsForHash().increment(COMIC_VIEW_HASH, comicId.toString(), 1);
            redisTemplate.opsForHash().increment(CHAPTER_VIEW_HASH, chapterId.toString(), 1);

            // TODO: Đẩy thêm 1 event "user_id đã đọc chapter_id" vào Kafka/Redis Queue để lưu Lịch sử đọc truyện
        }
    }
}
