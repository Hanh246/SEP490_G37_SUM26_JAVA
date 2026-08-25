package com.sep.comiverse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ChatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("senderId")
    private UUID senderId;

    @JsonProperty("senderName")
    private String senderName;

    @JsonProperty("senderAvatar")
    private String senderAvatar;

    @JsonProperty("chatType")
    private ChatType chatType;

    @JsonProperty("groupId")
    private UUID groupId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("imageUrl")
    private String imageUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
