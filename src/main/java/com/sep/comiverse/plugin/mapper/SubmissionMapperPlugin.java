package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class SubmissionMapperPlugin extends AbstractMapperPlugin<SubmissionEntity, SubmissionDTO, UUID> {

    @Autowired
    public SubmissionMapperPlugin(ModelMapper modelMapper) {
        super(SubmissionEntity.class, SubmissionDTO.class, UUID.class, modelMapper);
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "chapter", "submittedBy", "queueType");
    }
}
