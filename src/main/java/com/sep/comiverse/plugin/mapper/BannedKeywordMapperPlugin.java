package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.entity.BannedKeywordEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class BannedKeywordMapperPlugin extends AbstractMapperPlugin<BannedKeywordEntity, BannedKeywordDTO, UUID> {

    @Autowired
    public BannedKeywordMapperPlugin(ModelMapper modelMapper) {
        super(BannedKeywordEntity.class, BannedKeywordDTO.class, UUID.class, modelMapper);
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("word", "category");
    }
}
