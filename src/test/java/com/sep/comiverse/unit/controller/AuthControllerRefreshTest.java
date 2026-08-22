package com.sep.comiverse.unit.controller;

import com.sep.comiverse.controller.AuthController;
import com.sep.comiverse.dto.request.RefreshTokenRequest;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.AuthService;
import com.sep.comiverse.service.LoginDeviceService;
import com.sep.comiverse.service.PremiumPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    @Mock
    private PremiumPlanService premiumPlanService;
    @Mock
    private LoginDeviceService loginDeviceService;
    @Mock
    private IUserRepository userRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authService,
                jwtTokenUtil,
                premiumPlanService,
                loginDeviceService,
                userRepository
        );
    }

    @Test
    void activeRefreshTokenRotatesBothTokens() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UserEntity user = UserEntity.builder().status("ACTIVE").build();
        user.setId(userId);
        RefreshTokenRequest request = request("refresh-token");

        when(jwtTokenUtil.validateJwtToken("refresh-token")).thenReturn(true);
        when(jwtTokenUtil.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenUtil.getSubjectFromJwtToken("refresh-token")).thenReturn(userId.toString());
        when(jwtTokenUtil.getLoginDeviceIdFromToken("refresh-token")).thenReturn(deviceId);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(loginDeviceService.isActive(userId, deviceId)).thenReturn(true);
        when(jwtTokenUtil.generateToken(user, deviceId)).thenReturn("new-access-token");
        when(jwtTokenUtil.generateRefreshToken(user, deviceId)).thenReturn("new-refresh-token");

        var response = controller.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-access-token", response.getBody().getToken());
        assertEquals("new-refresh-token", response.getBody().getRefreshToken());
        verify(loginDeviceService).isActive(userId, deviceId);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        RefreshTokenRequest request = request("access-token");
        when(jwtTokenUtil.validateJwtToken("access-token")).thenReturn(true);
        when(jwtTokenUtil.isRefreshToken("access-token")).thenReturn(false);

        CustomException error = assertThrows(CustomException.class, () -> controller.refresh(request));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
        assertEquals(401, error.getCode());
    }

    @Test
    void revokedLoginDeviceCannotRefreshSession() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UserEntity user = UserEntity.builder().status("ACTIVE").build();
        user.setId(userId);
        RefreshTokenRequest request = request("refresh-token");

        when(jwtTokenUtil.validateJwtToken("refresh-token")).thenReturn(true);
        when(jwtTokenUtil.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenUtil.getSubjectFromJwtToken("refresh-token")).thenReturn(userId.toString());
        when(jwtTokenUtil.getLoginDeviceIdFromToken("refresh-token")).thenReturn(deviceId);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(loginDeviceService.isActive(userId, deviceId)).thenReturn(false);

        CustomException error = assertThrows(CustomException.class, () -> controller.refresh(request));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
        assertTrue(error.getMessage().toLowerCase().contains("refresh token"));
    }

    private RefreshTokenRequest request(String value) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(value);
        return request;
    }
}
