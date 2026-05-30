package com.sep.comiverse.common.plugin;

import java.util.List;

public interface IMapperPluginDetail<MODEL, DTO, ID> extends IMapperPlugin {
    Class<MODEL> getModelClass();

    Class<DTO> getDtoClass();

    Class<ID> getIdClass();

    // Convert between MODEL and DTO
    DTO toDto(MODEL model);

    MODEL toModel(DTO dto);

    // Update existing model with DTO data
    MODEL updateModel(MODEL existingModel, DTO dto);

    // Get the name field for search functionality
    List<String> getSearchableFieldNames();
}
