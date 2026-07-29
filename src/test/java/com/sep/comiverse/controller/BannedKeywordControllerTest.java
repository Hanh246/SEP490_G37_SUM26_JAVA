package com.sep.comiverse.controller;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.BannedKeywordCrudPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.sep.comiverse.service.ChatService;

@ExtendWith(MockitoExtension.class)
class BannedKeywordControllerTest {

    @Mock
    private BannedKeywordCrudPlugin crudPlugin;

    @Mock
    private ChatService chatService;

    private BannedKeywordController controller;

    @BeforeEach
    void setUp() {
        controller = new BannedKeywordController(crudPlugin, chatService);
    }

    @Test
    void listAllReturnsListOfKeywords() {
        // Arrange
        BannedKeywordDTO kw1 = BannedKeywordDTO.builder().id(UUID.randomUUID()).word("badword1").build();
        BannedKeywordDTO kw2 = BannedKeywordDTO.builder().id(UUID.randomUUID()).word("badword2").build();
        when(crudPlugin.listAll()).thenReturn(Arrays.asList(kw1, kw2));

        // Act
        ResponseEntity<BaseResponse<List<BannedKeywordDTO>>> response = controller.listAll();

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().size());
        verify(crudPlugin, times(1)).listAll();
    }

    @Test
    void createReturnsSavedKeyword() {
        // Arrange
        BannedKeywordDTO requestDto = BannedKeywordDTO.builder().word("newword").build();
        BannedKeywordDTO savedDto = BannedKeywordDTO.builder().id(UUID.randomUUID()).word("newword").build();
        when(crudPlugin.create(requestDto)).thenReturn(savedDto);

        // Act
        ResponseEntity<BaseResponse<BannedKeywordDTO>> response = controller.create(requestDto);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals("newword", response.getBody().getData().getWord());
        verify(crudPlugin, times(1)).create(requestDto);
    }

    @Test
    void deleteCallsCrudPlugin() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        ResponseEntity<BaseResponse<Void>> response = controller.delete(id);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        verify(crudPlugin, times(1)).delete(id);
    }
}
