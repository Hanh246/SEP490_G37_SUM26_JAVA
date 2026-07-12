package com.sep.comiverse.controller;

import com.sep.comiverse.dto.AuditLogDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.repository.IAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
@Tag(name = "Audit Log", description = "APIs for viewing system-wide moderator actions audit logs")
public class AuditLogController {

    private final IAuditLogRepository auditLogRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/all")
    @Operation(summary = "Retrieve all audit logs", description = "Get sorted activity audit logs in chronological order")
    public ResponseEntity<BaseResponse<List<AuditLogDTO>>> getAllAuditLogs() {
        List<AuditLogDTO> dtos = auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(log -> modelMapper.map(log, AuditLogDTO.class))
                .collect(Collectors.toList());

        return ResponseEntity.ok(BaseResponse.<List<AuditLogDTO>>builder()
                .success(true)
                .data(dtos)
                .build());
    }
}
