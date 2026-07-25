package com.sep.comiverse.config;

import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceEventListener {

    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UserPrincipal principal = resolvePrincipal(accessor.getUser());
        if (principal != null) {
            userPresenceService.connected(principal.getId(), accessor.getSessionId());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        userPresenceService.disconnected(event.getSessionId());
    }

    private UserPrincipal resolvePrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        return null;
    }
}
