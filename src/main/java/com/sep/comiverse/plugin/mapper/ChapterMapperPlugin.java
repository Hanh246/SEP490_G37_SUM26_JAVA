package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.IComicRepository;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ChapterMapperPlugin
        extends AbstractMapperPlugin<ChapterEntity, ChapterDTO, UUID> {
    private final IComicRepository comicRepository;
    private final com.sep.comiverse.repository.IUserRepository userRepository;
    private final java.util.Map<UUID, String> moderatorNameCache = new java.util.concurrent.ConcurrentHashMap<>();

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
    public ChapterMapperPlugin(ModelMapper modelMapper, IComicRepository comicRepository, com.sep.comiverse.repository.IUserRepository userRepository) {
        super(ChapterEntity.class, ChapterDTO.class, UUID.class, modelMapper);
        this.comicRepository = comicRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ChapterDTO toDto(ChapterEntity model) {
        if (model == null) return null;

        ChapterDTO dto = super.toDto(model);

        if (model.getComic() != null) {
            dto.setComicId(model.getComic().getId());
        }
        
        dto.setApprovedBy(resolveModeratorName(model.getApprovedById()));
        dto.setRejectedBy(resolveModeratorName(model.getRejectedById()));

        return dto;
    }

    @Override
    protected void performCustomUpdate(ChapterEntity model, ChapterDTO dto) {
        if (dto.getComicId() != null) {
            ComicEntity comic = comicRepository.findById(dto.getComicId())
                    .orElseThrow(() -> new EntityNotFoundException("Comic with id " + dto.getComicId() + " not found"));
            model.setComic(comic);
        }
    }

    @Override
    protected void performCustomCreate(ChapterEntity model, ChapterDTO dto) {
        if (dto.getComicId() != null) {
            ComicEntity comic = comicRepository.findById(dto.getComicId())
                    .orElseThrow(() -> new EntityNotFoundException("Comic with id " + dto.getComicId() + " not found"));
            model.setComic(comic);

            if (dto.getChapterNumber() != null) {
                comic.setLatestChapterNumber(dto.getChapterNumber());
            }

            comic.setLastChapterUpdatedAt(Instant.now());
        }
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("chapterNumber");
    }
}
