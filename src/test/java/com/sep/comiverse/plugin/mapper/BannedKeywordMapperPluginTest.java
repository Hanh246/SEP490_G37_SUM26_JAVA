package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.entity.BannedKeywordEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BannedKeywordMapperPluginTest {

    private ModelMapper modelMapper;
    private BannedKeywordMapperPlugin mapperPlugin;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapper();
        mapperPlugin = new BannedKeywordMapperPlugin(modelMapper);
    }

    @Test
    void toDtoMapsFieldsCorrectly() {
        // Arrange
        UUID id = UUID.randomUUID();
        BannedKeywordEntity entity = BannedKeywordEntity.builder()
                .word("toxic")
                .category("Hate")
                .severity("HIGH")
                .build();
        entity.setId(id);

        // Act
        BannedKeywordDTO dto = mapperPlugin.toDto(entity);

        // Assert
        assertEquals(id, dto.getId());
        assertEquals("toxic", dto.getWord());
        assertEquals("Hate", dto.getCategory());
        assertEquals("HIGH", dto.getSeverity());
    }

    @Test
    void toModelMapsFieldsCorrectly() {
        // Arrange
        UUID id = UUID.randomUUID();
        BannedKeywordDTO dto = BannedKeywordDTO.builder()
                .id(id)
                .word("toxic")
                .category("Hate")
                .severity("HIGH")
                .build();

        // Act
        BannedKeywordEntity entity = mapperPlugin.toModel(dto);

        // Assert
        assertEquals(id, entity.getId());
        assertEquals("toxic", entity.getWord());
        assertEquals("Hate", entity.getCategory());
        assertEquals("HIGH", entity.getSeverity());
    }

    @Test
    void getSearchableFieldNamesReturnsCorrectFields() {
        // Act
        List<String> searchableFields = mapperPlugin.getSearchableFieldNames();

        // Assert
        assertEquals(2, searchableFields.size());
        assertTrue(searchableFields.contains("word"));
        assertTrue(searchableFields.contains("category"));
    }
}
