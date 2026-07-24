package com.sep.comiverse.service;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    public void log(String actionType, String description) {
        // Audit logging disabled to optimize database performance and eliminate risk
    }
}
