package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.UUID;

@Component
public class ComicMapperPlugin extends AbstractMapperPlugin<ComicEntity, ComicDTO, UUID> {

    @Autowired
    public ComicMapperPlugin(ModelMapper modelMapper) {
        super(ComicEntity.class, ComicDTO.class, UUID.class, modelMapper);
    }

    @Override
    public ComicDTO toDto(ComicEntity model) {
        if (model == null) return null;
        ComicDTO dto = super.toDto(model);
        if (model.getGenres() != null && !model.getGenres().trim().isEmpty()) {
            dto.setGenres(Arrays.stream(model.getGenres().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList()));
        } else {
            dto.setGenres(List.of());
        }
        return dto;
    }

    @Override
    protected void performCustomUpdate(ComicEntity model, ComicDTO dto) {
        if (dto.getGenres() != null) {
            model.setGenres(String.join(",", dto.getGenres()));
        }
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "author", "projectTeam");
    }
}
