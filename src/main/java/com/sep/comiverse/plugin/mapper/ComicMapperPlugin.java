package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IGenreRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ComicMapperPlugin extends AbstractMapperPlugin<ComicEntity, ComicDTO, UUID> {
    private final IGenreRepository genreRepository;

    @Autowired
    public ComicMapperPlugin(ModelMapper modelMapper, IGenreRepository genreRepository) {
        super(ComicEntity.class, ComicDTO.class, UUID.class, modelMapper);
        this.genreRepository = genreRepository;
    }

    @Override
    public ComicDTO toDto(ComicEntity model) {
        if (model == null) return null;

        ComicDTO dto = super.toDto(model);

        if (model.getGenres() != null) {
            Set<GenreDTO> genreDtos = model.getGenres().stream()
                    .map(genreEntity -> modelMapper.map(genreEntity, GenreDTO.class))
                    .collect(Collectors.toSet());
            dto.setGenres(genreDtos);
        } else {
            dto.setGenres(new HashSet<>());
        }

        return dto;
    }

    @Override
    protected void performCustomUpdate(ComicEntity model, ComicDTO dto) {
        if (dto.getGenreIds() != null && !dto.getGenreIds().isEmpty()) {
            List<GenreEntity> genreEntities = genreRepository.findAllById(dto.getGenreIds());

            model.setGenres(new HashSet<>(genreEntities));

            List<UUID> validGenreIds = genreEntities.stream()
                    .map(GenreEntity::getId)
                    .toList();
            model.setGenreIds(validGenreIds);
        } else {
            model.setGenres(new HashSet<>());
            model.setGenreIds(new ArrayList<>());
        }
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "author");
    }
}
