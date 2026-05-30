package com.sep.comiverse.common.plugin;

import com.sep.comiverse.common.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.common.entity.BaseEntity;
import com.sep.comiverse.common.repository.AbstractCrudRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class AbstractCrudPlugin<MODEL extends BaseEntity, DTO, ID, P extends PaginationSearchDTO>
        implements ICrudPluginDetail<DTO, ID, P> {

    private final Class<MODEL> modelClass;
    protected final AbstractCrudRepository<MODEL, ID> repository;
    protected final PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;
    protected final IMapperPluginDetail<MODEL, DTO, ID> plugin;

    @Override
    public boolean supports(Class<?> delimiter) {
        return modelClass.equals(delimiter);
    }

    @SuppressWarnings("unchecked")
    protected AbstractCrudPlugin(AbstractCrudRepository<MODEL, ID> repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                                 Class<MODEL> modelClass) {
        this.modelClass = modelClass;
        this.repository = repository;
        this.pluginRegistry = pluginRegistry;
        var plugin = pluginRegistry.getPluginFor(modelClass)
                .orElseThrow(() -> new IllegalStateException("No plugin found for " + modelClass.getSimpleName()));
        this.plugin = (IMapperPluginDetail<MODEL, DTO, ID>) plugin;
    }

    @Override
    @Transactional
    public DTO create(DTO dto) throws RuntimeException {
        MODEL model = plugin.toModel(dto);
        MODEL savedModel = repository.save(model);
        return plugin.toDto(savedModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DTO> read(ID id) {
        return repository.findById(id)
                .map(plugin::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DTO> list(P paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();

        Page<DTO> data;
        if (paginationDTO.getSearch() == null || paginationDTO.getSearch().isEmpty()) {
            data = repository.findAll(pageable).map(plugin::toDto);
        } else {
            data = repository
                    .findAll(repository.contains(plugin.getSearchableFieldNames(), paginationDTO.getSearch()), pageable)
                    .map(plugin::toDto);
        }

        return data;
    }

    @Override
    @Transactional
    public DTO update(ID id, DTO dto) {
        MODEL existingModel = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity with id " + id + " not found"));

        MODEL updatedModel = plugin.updateModel(existingModel, dto);
        MODEL savedModel = repository.save(updatedModel);
        return plugin.toDto(savedModel);
    }

    @Override
    @Transactional
    public void delete(ID id) {
        var deleteEntity = repository.findById(id);
        if (deleteEntity.isEmpty()) {
            throw new EntityNotFoundException("Entity with id " + id + " not found");
        }
        repository.softDeleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DTO> listAll() {
        return repository.findAll().stream().map(plugin::toDto).toList();
    }
}
