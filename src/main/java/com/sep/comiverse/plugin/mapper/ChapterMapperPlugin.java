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

    @Autowired
    public ChapterMapperPlugin(ModelMapper modelMapper, IComicRepository comicRepository) {
        super(ChapterEntity.class, ChapterDTO.class, UUID.class, modelMapper);
        this.comicRepository = comicRepository;
    }

    @Override
    public ChapterDTO toDto(ChapterEntity model) {
        if (model == null) return null;

        ChapterDTO dto = super.toDto(model);

        if (model.getComic() != null) {
            dto.setComicId(model.getComic().getId());
        }

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
