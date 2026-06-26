package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.plugin.crud.ForumThreadCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/forum-threads")
public class ForumThreadController extends BaseController<ForumThreadEntity, ForumThreadDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ForumThreadController(ForumThreadCrudPlugin crud) {
        super(crud, ForumThreadEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ForumThreadDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ForumThreadDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }
}
