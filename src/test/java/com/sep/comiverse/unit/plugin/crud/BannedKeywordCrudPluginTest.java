package com.sep.comiverse.unit.plugin.crud;

import com.sep.comiverse.plugin.crud.BannedKeywordCrudPlugin;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.entity.BannedKeywordEntity;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.plugin.IMapperPluginDetail;
import com.sep.comiverse.repository.IBannedKeywordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.plugin.core.PluginRegistry;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BannedKeywordCrudPluginTest {

    @Mock
    private IBannedKeywordRepository repository;

    @Mock
    private PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;

    @Mock
    private IMapperPluginDetail<BannedKeywordEntity, BannedKeywordDTO, UUID> mapperPlugin;

    private BannedKeywordCrudPlugin crudPlugin;

    @BeforeEach
    void setUp() {
        when(pluginRegistry.getPluginFor(BannedKeywordEntity.class)).thenReturn(Optional.of(mapperPlugin));
        crudPlugin = new BannedKeywordCrudPlugin(repository, pluginRegistry);
    }

    @Test
    void testCreateBannedKeyword() {
        // Arrange
        BannedKeywordDTO requestDto = BannedKeywordDTO.builder().word("badword").category("Profanity").severity("HIGH").build();
        BannedKeywordEntity entity = BannedKeywordEntity.builder().word("badword").category("Profanity").severity("HIGH").build();
        BannedKeywordEntity savedEntity = BannedKeywordEntity.builder().word("badword").category("Profanity").severity("HIGH").build();
        savedEntity.setId(UUID.randomUUID());
        BannedKeywordDTO responseDto = BannedKeywordDTO.builder().id(savedEntity.getId()).word("badword").category("Profanity").severity("HIGH").build();

        when(mapperPlugin.toModel(requestDto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapperPlugin.toDto(savedEntity)).thenReturn(responseDto);

        // Act
        BannedKeywordDTO result = crudPlugin.create(requestDto);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        verify(repository, times(1)).save(entity);
    }
}
