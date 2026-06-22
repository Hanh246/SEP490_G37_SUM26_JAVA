package com.sep.comiverse.plugin;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface ICrudPluginDetail<DTO, ID, P extends PaginationSearchDTO> extends ICrudPlugin {
    DTO create(DTO dto) throws Exception;

    DTO update(ID id,@Valid DTO dto) throws RuntimeException;

    void delete(ID id) throws RuntimeException;

    Optional<DTO> read(ID id) throws RuntimeException;

    Page<DTO> list(P paginationDTO) throws RuntimeException;

    List<DTO> listAll() throws RuntimeException;
}
