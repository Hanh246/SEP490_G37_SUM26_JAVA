package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comics")
public class ComicController extends BaseController<ComicEntity, ComicDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ComicController(ComicCrudPlugin crud) {
        super(crud, ComicEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }
}
