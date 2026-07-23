package com.sep.comiverse.config;

import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final IProjectTeamRepository projectTeamRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (StringUtils.hasText(token) && jwtTokenUtil.validateJwtToken(token)) {
                String userIdStr = jwtTokenUtil.getSubjectFromJwtToken(token);
                UserDetails userDetails = customUserDetailsService.loadUserById(UUID.fromString(userIdStr));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                accessor.setUser(auth);
                logger.info("WebSocket User authenticated: {}", userDetails.getUsername());
            } else {
                logger.warn("WebSocket authentication failed: Invalid or missing token");
                throw new CustomException(401, "UNAUTHORIZED_WEBSOCKET_CONNECT", HttpStatus.UNAUTHORIZED);
            }
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscriptionPermission(accessor);
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // Try Authorization header
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String bearerToken = authorization.get(0);
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            return bearerToken;
        }

        // Try 'token' header fallback
        List<String> tokenHeader = accessor.getNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            return tokenHeader.get(0);
        }

        return null;
    }

    private void validateSubscriptionPermission(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || !(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            logger.warn("WebSocket subscribe rejected: Unauthenticated user");
            throw new CustomException(401, "UNAUTHORIZED_WEBSOCKET_SUBSCRIBE", HttpStatus.UNAUTHORIZED);
        }

        String destination = accessor.getDestination();
        if (destination == null) return;

        UUID userId = principal.getId();

        // 1. Group Chat authorization check: /topic/chat/group/{groupId}
        if (destination.startsWith("/topic/chat/group/")) {
            String groupIdStr = destination.substring("/topic/chat/group/".length());
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                boolean isMember = projectTeamRepository.isUserMemberOfTeam(groupId, userId);
                if (!isMember) {
                    logger.warn("User {} attempted to subscribe to group chat {} without membership", userId, groupId);
                    throw new CustomException(403, "FORBIDDEN_GROUP_CHAT_ACCESS", HttpStatus.FORBIDDEN);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid group ID in subscription path: {}", groupIdStr);
                throw new CustomException(400, "INVALID_GROUP_ID", HttpStatus.BAD_REQUEST);
            }
        }

        // 2. Notification authorization check: /topic/notifications/{userId}
        if (destination.startsWith("/topic/notifications/")) {
            String targetUserIdStr = destination.substring("/topic/notifications/".length());
            try {
                UUID targetUserId = UUID.fromString(targetUserIdStr);
                if (!userId.equals(targetUserId)) {
                    logger.warn("User {} attempted to subscribe to notification channel of user {}", userId, targetUserId);
                    throw new CustomException(403, "FORBIDDEN_NOTIFICATION_ACCESS", HttpStatus.FORBIDDEN);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid user ID in notification path: {}", targetUserIdStr);
                throw new CustomException(400, "INVALID_USER_ID", HttpStatus.BAD_REQUEST);
            }
        }
    }
}
