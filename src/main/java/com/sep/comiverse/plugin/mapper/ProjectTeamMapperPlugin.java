package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProjectTeamMapperPlugin extends AbstractMapperPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID> {

    @Autowired
    public ProjectTeamMapperPlugin(ModelMapper modelMapper) {
        super(ProjectTeamEntity.class, ProjectTeamDTO.class, UUID.class, modelMapper);
    }

    @Override
    public ProjectTeamDTO toDto(ProjectTeamEntity model) {
        if (model == null) return null;
        ProjectTeamDTO dto = super.toDto(model);
        dto.setComicTitle(model.getComicName());
        dto.setNotes(model.getNotes());
        if (model.getChaptersList() != null && org.hibernate.Hibernate.isInitialized(model.getChaptersList())) {
            dto.setChaptersList(model.getChaptersList().stream()
                    .map(chap -> {
                        var chapDto = new com.sep.comiverse.dto.ChapterDTO();
                        chapDto.setId(chap.getId());
                        
                        // Extract comic ID directly from the Hibernate proxy without running a database query
                        UUID comicId = null;
                        if (chap.getComic() != null) {
                            if (chap.getComic() instanceof org.hibernate.proxy.HibernateProxy proxy) {
                                comicId = (UUID) proxy.getHibernateLazyInitializer().getIdentifier();
                            } else {
                                comicId = chap.getComic().getId();
                            }
                        }
                        chapDto.setComicId(comicId);
                        
                        chapDto.setChapterNumber(chap.getChapterNumber());
                        chapDto.setTitle(chap.getTitle());
                        chapDto.setNum("Chapter " + chap.getChapterNumber());
                        chapDto.setDate("Just now");
                        return chapDto;
                    })
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    @Override
    protected void configureModelMapper() {
        super.configureModelMapper();
        modelMapper.typeMap(ProjectTeamDTO.class, ProjectTeamEntity.class)
                .addMappings(mapper -> mapper.skip(ProjectTeamEntity::setChaptersList));
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "comicName", "leaderName");
    }
}
