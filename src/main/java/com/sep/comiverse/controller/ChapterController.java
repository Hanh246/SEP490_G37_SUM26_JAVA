package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chapters")
public class ChapterController extends BaseController<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ChapterController(ChapterCrudPlugin crud) {
        super(crud, ChapterEntity.class);
    }
}
