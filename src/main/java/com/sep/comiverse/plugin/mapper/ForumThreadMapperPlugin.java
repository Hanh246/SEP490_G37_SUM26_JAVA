package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class ForumThreadMapperPlugin extends AbstractMapperPlugin<ForumThreadEntity, ForumThreadDTO, UUID> {

    @Autowired
    public ForumThreadMapperPlugin(ModelMapper modelMapper) {
        super(ForumThreadEntity.class, ForumThreadDTO.class, UUID.class, modelMapper);
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "author", "content");
    }
}
