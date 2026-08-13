package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.AuthResponse;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.service.LoginDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile-friendly Google Sign-In endpoint.
 * The Flutter app uses google_sign_in to get a Google ID Token, then sends it here.
 * This endpoint verifies it against Google's tokeninfo API, upserts the user, and returns a JWT pair.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GoogleAuthController {

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final LoginDeviceService loginDeviceService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * POST /api/auth/google-login
     * Body: { "idToken": "<Google ID Token>" }
     */
    @PostMapping("/google-login")
    @SuppressWarnings("unchecked")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody Map<String, String> body) {
        String idToken = body.get("idToken");
        if (!StringUtils.hasText(idToken)) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> tokenInfo;
        try {
            tokenInfo = restTemplate.getForObject(
                    GOOGLE_TOKEN_INFO_URL + idToken,
                    Map.class
            );
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }

        if (tokenInfo == null || tokenInfo.containsKey("error_description")) {
            return ResponseEntity.status(401).build();
        }

        String email   = (String) tokenInfo.get("email");
        String name    = (String) tokenInfo.get("name");
        String sub     = (String) tokenInfo.get("sub");
        String picture = (String) tokenInfo.get("picture");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(sub)) {
            return ResponseEntity.status(401).build();
        }

        Optional<UserEntity> existing = userRepository.findByEmail(email);
        UserEntity user;
        if (existing.isPresent()) {
            user = existing.get();
            user.setProviderId(sub);
            if (StringUtils.hasText(picture) && !StringUtils.hasText(user.getAvatarUrl())) {
                user.setAvatarUrl(picture);
            }
            userRepository.save(user);
        } else {
            RoleEntity readerRole = roleRepository.findByRoleName("READER")
                    .orElseThrow(() -> new RuntimeException("Default role 'READER' not found"));

            String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
            if (baseUsername.length() < 3) baseUsername = "user_" + baseUsername;
            String username = baseUsername;
            int attempt = 0;
            while (userRepository.findByUsername(username).isPresent()) {
                username = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 4);
                if (++attempt > 10) username = "guser_" + UUID.randomUUID().toString().substring(0, 8);
            }

            user = UserEntity.builder()
                    .email(email)
                    .fullName(StringUtils.hasText(name) ? name : username)
                    .username(username)
                    .provider("GOOGLE")
                    .providerId(sub)
                    .avatarUrl(picture)
                    .status("ACTIVE")
                    .role(readerRole)
                    .build();
            userRepository.save(user);
        }

        AuthRequest deviceRequest = new AuthRequest();
        deviceRequest.setDeviceId(body.get("deviceId"));
        deviceRequest.setDeviceName(body.get("deviceName"));
        deviceRequest.setPlatform(body.get("platform"));
        LoginDeviceService.LoginDecision decision = loginDeviceService.beginLogin(user, deviceRequest);
        if (decision.verificationRequired()) {
            return ResponseEntity.status(202).body(AuthResponse.builder()
                    .deviceVerificationRequired(true)
                    .deviceChallengeId(decision.challengeId())
                    .deviceChallengeExpiresAt(decision.expiresAt())
                    .devices(decision.devices())
                    .build());
        }
        String token        = jwtTokenUtil.generateToken(user, decision.deviceId());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user, decision.deviceId());
        return ResponseEntity.ok(new AuthResponse(token, refreshToken));
    }
}
