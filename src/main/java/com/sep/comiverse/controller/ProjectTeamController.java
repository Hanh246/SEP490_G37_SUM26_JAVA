package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.crud.ProjectTeamCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/project-teams")
public class ProjectTeamController extends BaseController<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ProjectTeamController(ProjectTeamCrudPlugin crud) {
        super(crud, ProjectTeamEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ProjectTeamDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ProjectTeamDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }
}
