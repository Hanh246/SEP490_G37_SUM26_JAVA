package com.sep.comiverse.controller;

import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.crud.GenreCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/genre")
public class GenreController
        extends BaseController<GenreEntity, GenreDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public GenreController(GenreCrudPlugin crud) {
        super(crud, GenreEntity.class);
    }
}
