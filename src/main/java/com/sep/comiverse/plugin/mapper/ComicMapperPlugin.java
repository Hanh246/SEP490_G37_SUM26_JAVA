package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ComicMapperPlugin extends AbstractMapperPlugin<ComicEntity, ComicDTO, UUID> {
    private final IGenreRepository genreRepository;
    private final IUserRepository userRepository;
    private final Map<UUID, String> authorNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public ComicMapperPlugin(ModelMapper modelMapper, IGenreRepository genreRepository, IUserRepository userRepository) {
        super(ComicEntity.class, ComicDTO.class, UUID.class, modelMapper);
        this.genreRepository = genreRepository;
        this.userRepository = userRepository;

        // Skip mapping genres from DTO to Entity to prevent ModelMapper map exceptions
        modelMapper.typeMap(ComicDTO.class, ComicEntity.class)
                .addMappings(mapper -> mapper.skip(ComicEntity::setGenres));
    }

    @Override
    public ComicDTO toDto(ComicEntity model) {
        if (model == null) return null;

        // Auto-pause if last chapter update was > 30 days ago and currently ONGOING
        if (model.getStatus() == com.sep.comiverse.constants.ComicStatus.ONGOING && model.getLastChapterUpdatedAt() != null) {
            java.time.Instant thirtyDaysAgo = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
            if (model.getLastChapterUpdatedAt().isBefore(thirtyDaysAgo)) {
                model.setStatus(com.sep.comiverse.constants.ComicStatus.PAUSED);
                // JPA dirty checking will update this status to DB during transaction commit
            }
        }

        ComicDTO dto = super.toDto(model);

        // Resolve author name using cache to prevent connection pool exhaustion deadlocks
        if (model.getAuthorId() != null) {
            String cachedName = authorNameCache.get(model.getAuthorId());
            if (cachedName != null) {
                dto.setAuthorName(cachedName);
            } else {
                userRepository.findById(model.getAuthorId()).ifPresentOrElse(user -> {
                    String name = user.getFullName() != null && !user.getFullName().isBlank() 
                            ? user.getFullName() 
                            : user.getUsername();
                    authorNameCache.put(model.getAuthorId(), name);
                    dto.setAuthorName(name);
                }, () -> {
                    dto.setAuthorName("Unknown Author");
                });
            }
        } else {
            dto.setAuthorName("Unknown Author");
        }

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
        return List.of("title", "authorName");
    }
}
