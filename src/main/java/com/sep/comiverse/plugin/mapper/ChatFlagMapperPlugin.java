package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ChatFlagDTO;
import com.sep.comiverse.entity.ChatFlagEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class ChatFlagMapperPlugin extends AbstractMapperPlugin<ChatFlagEntity, ChatFlagDTO, UUID> {

    @Autowired
    public ChatFlagMapperPlugin(ModelMapper modelMapper) {
        super(ChatFlagEntity.class, ChatFlagDTO.class, UUID.class, modelMapper);
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("user", "message", "reason");
    }
}
