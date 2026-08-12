package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IForumThreadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class ForumThreadCrudPlugin extends AbstractCrudPlugin<ForumThreadEntity, ForumThreadDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ForumThreadCrudPlugin(IForumThreadRepository repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ForumThreadEntity.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ForumThreadDTO> list(PaginationSearchDTO paginationDTO) {
        Sort sort = Sort.by(Sort.Direction.DESC, "isPinned")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable pageable = PageRequest.of(
                paginationDTO.getPage() - 1,
                paginationDTO.getSize(),
                sort
        );

        if (paginationDTO.getSearch() == null || paginationDTO.getSearch().isBlank()) {
            return repository.findAll(pageable).map(plugin::toDto);
        }
        return repository.findAll(
                        repository.contains(plugin.getSearchableFieldNames(), paginationDTO.getSearch()),
                        pageable
                )
                .map(plugin::toDto);
    }
}
