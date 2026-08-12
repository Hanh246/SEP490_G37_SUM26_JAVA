package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.AuditLogService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuditLogServiceTest {

    private final AuditLogService service = new AuditLogService();

    @Test
    void log_isNoOpAndNeverThrows() {
        assertDoesNotThrow(() -> service.log("LOGIN", "user signed in"));
        assertDoesNotThrow(() -> service.log(null, null));
    }
}
