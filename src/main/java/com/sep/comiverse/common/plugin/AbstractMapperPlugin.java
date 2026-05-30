package com.sep.comiverse.common.plugin;

import com.sep.comiverse.common.entity.BaseEntity;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;

@RequiredArgsConstructor
public abstract class AbstractMapperPlugin<MODEL extends BaseEntity, DTO, ID>
        implements IMapperPluginDetail<MODEL, DTO, ID> {

    private final Class<MODEL> modelClass;
    private final Class<DTO> dtoClass;
    private final Class<ID> idClass;

    protected final ModelMapper modelMapper;

    @Override
    public Class<MODEL> getModelClass() {
        return modelClass;
    }

    @Override
    public Class<DTO> getDtoClass() {
        return dtoClass;
    }

    @Override
    public Class<ID> getIdClass() {
        return idClass;
    }

    @Override
    public boolean supports(Class<?> delimiter) {
        return modelClass.equals(delimiter);
    }

    @Override
    public DTO toDto(MODEL model) {
        if (model == null)
            return null;
        return modelMapper.map(model, dtoClass);
    }

    @Override
    public MODEL toModel(DTO dto) {
        if (dto == null)
            return null;
        var model = modelMapper.map(dto, modelClass);
        performCustomUpdate(model, dto);
        return model;
    }

    @Override
    public MODEL updateModel(MODEL existingModel, DTO dto) {
        if (dto == null)
            return existingModel;

        var id = existingModel.getId();
        var createdAt = existingModel.getCreatedAt();
        modelMapper.map(dto, existingModel);
        existingModel.setId(id);
        existingModel.setCreatedAt(createdAt);
        // Perform any custom update logic
        performCustomUpdate(existingModel, dto);

        return existingModel;
    }

    /**
     * Override this method to perform custom update logic
     * that cannot be handled by ModelMapper automatically
     */
    protected void performCustomUpdate(MODEL existingModel, DTO dto) {
        // Default implementation does nothing
        // Override in specific plugins for custom logic
    }

    /**
     * Override this method to configure ModelMapper with custom mappings
     * This method is called during plugin initialization
     */
    protected void configureModelMapper() {
        modelMapper.getConfiguration()
                .setFieldMatchingEnabled(true);
    }

    /**
     * Initialize the plugin - called after dependency injection
     */
    @jakarta.annotation.PostConstruct
    protected void init() {
        configureModelMapper();
    }
}
