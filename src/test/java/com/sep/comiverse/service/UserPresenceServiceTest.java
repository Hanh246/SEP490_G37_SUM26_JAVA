package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private IUserRepository userRepository;

    private UserPresenceService userPresenceService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userPresenceService = new UserPresenceService(userRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void userIsOnlineUntilTheirLastSessionDisconnects() {
        userPresenceService.connected(userId, "session-1");
        userPresenceService.connected(userId, "session-2");

        assertTrue(userPresenceService.isOnline(userId));

        userPresenceService.disconnected("session-1");
        assertTrue(userPresenceService.isOnline(userId));

        userPresenceService.disconnected("session-2");
        assertFalse(userPresenceService.isOnline(userId));
        verify(userRepository, times(3)).updateLastSeenAt(eq(userId), any(Instant.class));
    }

    @Test
    void unknownSessionDoesNotChangePresence() {
        userPresenceService.disconnected("unknown-session");

        assertFalse(userPresenceService.isOnline(userId));
        verify(userRepository, times(0)).updateLastSeenAt(eq(userId), any(Instant.class));
    }
}
