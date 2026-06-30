package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GenreMapperPlugin
        extends AbstractMapperPlugin<GenreEntity, GenreDTO, UUID> {

    @Autowired
    public GenreMapperPlugin(ModelMapper modelMapper) {
        super(GenreEntity.class, GenreDTO.class, UUID.class, modelMapper);
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("name");
    }
}
