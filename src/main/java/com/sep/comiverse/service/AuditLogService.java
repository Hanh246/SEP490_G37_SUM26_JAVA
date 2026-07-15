package com.sep.comiverse.service;

import com.sep.comiverse.entity.AuditLogEntity;
import com.sep.comiverse.repository.IAuditLogRepository;
import com.sep.comiverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final IAuditLogRepository auditLogRepository;

    @Transactional
    public void log(String actionType, String description) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID actorId = null;
        String actorName = "System";

        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            actorId = principal.getId();
            actorName = principal.getFullName() != null && !principal.getFullName().trim().isEmpty()
                    ? principal.getFullName()
                    : principal.getUsername();
        }

        AuditLogEntity log = AuditLogEntity.builder()
                .actorId(actorId)
                .actorName(actorName)
                .actionType(actionType)
                .description(description)
                .build();
        log.setCreatedAt(Instant.now());
        log.setUpdatedAt(Instant.now());
        log.setDeleted(false);

        auditLogRepository.save(log);
    }
}
