package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IAuthorRepository;
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
    private final IAuthorRepository authorRepository;
    private final IUserRepository userRepository;
    private final com.sep.comiverse.repository.IChapterRepository chapterRepository;
    private final Map<UUID, String> authorNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public ComicMapperPlugin(ModelMapper modelMapper,
                             IGenreRepository genreRepository,
                             IAuthorRepository authorRepository,
                             IUserRepository userRepository,
                             com.sep.comiverse.repository.IChapterRepository chapterRepository) {
        super(ComicEntity.class, ComicDTO.class, UUID.class, modelMapper);
        this.genreRepository = genreRepository;
        this.authorRepository = authorRepository;
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;

        // Skip mapping genres from DTO to Entity to prevent ModelMapper map exceptions
        modelMapper.typeMap(ComicDTO.class, ComicEntity.class)
                .addMappings(mapper -> mapper.skip(ComicEntity::setGenres));
    }

    @Override
    public ComicDTO toDto(ComicEntity model) {
        if (model == null) return null;

        // Keep the old inactivity rule, but use the current ComicEntity field.
        // PAUSED was removed together with ComicStatus; HIATUS is the matching
        // value in ComicPublicationStatus.
        if (model.getPublicationStatus() == com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING
                && model.getLastChapterUpdatedAt() != null) {
            java.time.Instant thirtyDaysAgo = java.time.Instant.now()
                    .minus(30, java.time.temporal.ChronoUnit.DAYS);
            if (model.getLastChapterUpdatedAt().isBefore(thirtyDaysAgo)) {
                model.setPublicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.HIATUS);
            }
        }

        ComicDTO dto = super.toDto(model);

        // ComicEntity.authorId currently stores the author user's UUID. Some legacy
        // records may store AuthorEntity.id, so resolve both forms. Public search also
        // uses AuthorEntity.displayName; returning the same value keeps Library,
        // Moderator and search results consistent.
        if (model.getAuthorId() != null) {
            UUID authorId = model.getAuthorId();
            String cachedName = authorNameCache.get(authorId);
            if (cachedName != null) {
                dto.setAuthorName(cachedName);
            } else {
                String resolvedName = authorRepository.findById(authorId)
                        .or(() -> authorRepository.findByUserIdAndDeletedFalse(authorId))
                        .map(author -> author.getDisplayName())
                        .filter(name -> name != null && !name.isBlank())
                        .orElseGet(() -> userRepository.findById(authorId)
                                .map(user -> user.getFullName() != null && !user.getFullName().isBlank()
                                        ? user.getFullName()
                                        : user.getUsername())
                                .filter(name -> name != null && !name.isBlank())
                                .orElse("Unknown Author"));

                authorNameCache.put(authorId, resolvedName);
                dto.setAuthorName(resolvedName);
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

        if (model.getId() != null) {
            long rejected = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                    model.getId(), com.sep.comiverse.entity.enums.ChapterStatus.REJECTED);
            long pending = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                    model.getId(), com.sep.comiverse.entity.enums.ChapterStatus.SUBMITTED_FOR_REVIEW);
            dto.setRejectedChapterCount((int) rejected);
            dto.setPendingChapterCount((int) pending);
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
        return List.of("title", "author", "language");
    }
}
