package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
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
    private final Map<UUID, String> moderatorNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String resolveModeratorName(UUID moderatorId) {
        if (moderatorId == null) return null;
        return moderatorNameCache.computeIfAbsent(moderatorId, id -> 
                userRepository.findById(id)
                        .map(user -> user.getFullName() != null && !user.getFullName().isBlank() 
                                ? user.getFullName() : user.getUsername())
                        .orElse("Unknown Moderator")
        );
    }

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

        ComicDTO dto = super.toDto(model);

        // Keep the old inactivity rule: PAUSED was removed together with ComicStatus; 
        // HIATUS is the matching value in ComicPublicationStatus.
        // Auto-HIATUS: Comics with no new chapter in 180 days (6 months) are marked HIATUS.
        // DO NOT modify the `model` directly in a readOnly transaction, modify the DTO instead!
        if (dto.getPublicationStatus() == com.sep.comiverse.entity.enums.ComicPublicationStatus.ONGOING
                && model.getLastChapterUpdatedAt() != null) {
            java.time.Instant sixMonthsAgo = java.time.Instant.now()
                    .minus(180, java.time.temporal.ChronoUnit.DAYS);
            if (model.getLastChapterUpdatedAt().isBefore(sixMonthsAgo)) {
                dto.setPublicationStatus(com.sep.comiverse.entity.enums.ComicPublicationStatus.HIATUS);
            }
        }

        // ComicEntity.authorId is the authenticated Author user's UUID in the
        // current Author flow. Legacy rows may contain AuthorEntity.id, so support
        // both forms. Do NOT cache this name in-process: AuthorEntity.displayName is
        // editable profile data and Comic Detail must reflect the latest value.
        if (model.getAuthorId() != null) {
            UUID authorId = model.getAuthorId();
            String resolvedName = authorRepository.findByUserIdAndDeletedFalse(authorId)
                    .or(() -> authorRepository.findById(authorId)
                            .filter(author -> !Boolean.TRUE.equals(author.getDeleted())))
                    .map(author -> author.getDisplayName())
                    .filter(name -> name != null && !name.isBlank())
                    // Compatibility only for malformed/legacy rows that have no
                    // AuthorEntity. Normal comics must resolve Author.displayName.
                    .orElseGet(() -> userRepository.findById(authorId)
                            .map(user -> user.getFullName() != null && !user.getFullName().isBlank()
                                    ? user.getFullName()
                                    : user.getUsername())
                            .filter(name -> name != null && !name.isBlank())
                            .orElse("Unknown Author"));

            dto.setAuthorName(resolvedName);
        } else {
            dto.setAuthorName("Unknown Author");
        }
        
        dto.setApprovedBy(resolveModeratorName(model.getApprovedById()));

        if (model.getGenres() != null) {
            Set<GenreDTO> genreDtos = model.getGenres().stream()
                    .map(genreEntity -> modelMapper.map(genreEntity, GenreDTO.class))
                    .collect(Collectors.toSet());
            dto.setGenres(genreDtos);
        } else {
            dto.setGenres(new HashSet<>());
        }

        if (model.getId() != null) {
            int storedCount = model.getChapterCount() != null ? model.getChapterCount() : 0;
            // Hack to bypass frontend bug on deployed instances:
            // The FE explicitly hides pending comics if chapterCount <= 0.
            // Since chapterCount only tracks PUBLISHED chapters, new comics were being hidden.
            if (storedCount == 0 &&
               (model.getModerationStatus() == ComicModerationStatus.SUBMITTED_FOR_REVIEW ||
                Boolean.TRUE.equals(model.getIsAppealed()))) {
                dto.setChapterCount(1);
            } else {
                dto.setChapterCount(storedCount);
            }
            dto.setRejectedChapterCount(0);
            dto.setPendingChapterCount(0);
        }
        dto.setIsAppealed(model.getIsAppealed() != null && model.getIsAppealed());
        dto.setAppealReason(model.getAppealReason());
        dto.setRejectionReason(model.getRejectionReason());

        return dto;
    }

    @Override
    protected void performCustomUpdate(ComicEntity model, ComicDTO dto) {
        if (dto.getGenreIds() != null && !dto.getGenreIds().isEmpty()) {
            List<GenreEntity> genreEntities = genreRepository.findAllById(dto.getGenreIds());
            model.setGenres(new HashSet<>(genreEntities));
        } else {
            model.setGenres(new HashSet<>());
        }
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "author", "language");
    }
}
