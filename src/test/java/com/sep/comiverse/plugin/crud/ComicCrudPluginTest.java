package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.plugin.IMapperPluginDetail;
import com.sep.comiverse.repository.IComicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.plugin.core.PluginRegistry;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ComicCrudPluginTest {

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;

    @Mock
    private IMapperPluginDetail<ComicEntity, ComicDTO, UUID> mapperPlugin;

    private ComicCrudPlugin comicCrudPlugin;

    private final UUID comicId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(pluginRegistry.getPluginFor(ComicEntity.class)).thenReturn(Optional.of(mapperPlugin));
        comicCrudPlugin = new ComicCrudPlugin(comicRepository, pluginRegistry);
    }

    @Test
    void testGetComicDetail_CacheHit() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);
        comic.setTitle("Cached Comic");
        comic.setViewCount(100L);
        comic.setLikeCount(50);
        comic.setSaveCount(10);

        ComicDTO cachedDto = new ComicDTO();
        cachedDto.setId(comicId);
        cachedDto.setTitle("Cached Comic");
        cachedDto.setViewCount(100L);
        cachedDto.setLikeCount(50);
        cachedDto.setSaveCount(10);

        when(comicRepository.findByIdAndDeletedFalseAndModerationStatus(comicId, com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED))
                .thenReturn(Optional.of(comic));
        when(mapperPlugin.toDto(comic)).thenReturn(cachedDto);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("Cached Comic", result.getTitle());
        assertEquals(100L, result.getViewCount());
    }

    @Test
    void testGetComicDetail_CacheMiss() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);
        comic.setTitle("DB Comic");
        comic.setViewCount(200L);
        comic.setLikeCount(80);
        comic.setSaveCount(15);

        ComicDTO loadedDto = new ComicDTO();
        loadedDto.setId(comicId);
        loadedDto.setTitle("DB Comic");
        loadedDto.setViewCount(200L);
        loadedDto.setLikeCount(80);
        loadedDto.setSaveCount(15);

        when(comicRepository.findByIdAndDeletedFalseAndModerationStatus(comicId, com.sep.comiverse.entity.enums.ComicModerationStatus.PUBLISHED))
                .thenReturn(Optional.of(comic));
        when(mapperPlugin.toDto(comic)).thenReturn(loadedDto);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("DB Comic", result.getTitle());
        assertEquals(200L, result.getViewCount());
    }
}
