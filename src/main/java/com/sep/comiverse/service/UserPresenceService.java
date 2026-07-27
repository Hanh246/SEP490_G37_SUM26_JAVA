package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private final IUserRepository userRepository;
    private final ConcurrentMap<String, UUID> usersBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public void connected(UUID userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        usersBySession.put(sessionId, userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        userRepository.updateLastSeenAt(userId, Instant.now());
    }

    public void disconnected(String sessionId) {
        if (sessionId == null) {
            return;
        }

        UUID userId = usersBySession.remove(sessionId);
        if (userId == null) {
            return;
        }

        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (!sessions.isEmpty()) {
                return;
            }
            sessionsByUser.remove(userId, sessions);
        }

        userRepository.updateLastSeenAt(userId, Instant.now());
    }

    public boolean isOnline(UUID userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
