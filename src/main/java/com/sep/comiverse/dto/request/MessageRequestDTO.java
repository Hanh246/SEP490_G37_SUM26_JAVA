package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ChatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequestDTO {

    @Schema(description = "Chat room type (GLOBAL or GROUP)", example = "GLOBAL")
    @NotNull(message = "chatType is required")
    @JsonProperty("chatType")
    private ChatType chatType;

    @Schema(description = "Group ID (required when chatType is GROUP)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    @JsonProperty("groupId")
    private UUID groupId;

    @Schema(description = "Message content", example = "Hello everyone!")
    @Size(max = 2000, message = "Content length cannot exceed 2000 characters")
    @JsonProperty("content")
    private String content;

    @Schema(description = "HTTPS URL of an optional uploaded chat image")
    @Size(max = 2048, message = "Image URL length cannot exceed 2048 characters")
    @JsonProperty("imageUrl")
    private String imageUrl;
}
