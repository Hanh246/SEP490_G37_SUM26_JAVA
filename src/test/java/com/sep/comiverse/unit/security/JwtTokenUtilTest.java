package com.sep.comiverse.unit.security;

import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(
                jwtTokenUtil,
                "jwtSecret",
                "unit-test-secret-that-is-at-least-thirty-two-bytes-long"
        );
        ReflectionTestUtils.setField(jwtTokenUtil, "jwtExpirationMs", 900_000L);
        jwtTokenUtil.init();

        RoleEntity role = RoleEntity.builder().roleName("READER").build();
        user = UserEntity.builder().role(role).status("ACTIVE").build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void generatedTokensHaveDistinctPurposesAndPreserveDeviceId() {
        UUID deviceId = UUID.randomUUID();

        String accessToken = jwtTokenUtil.generateToken(user, deviceId);
        String refreshToken = jwtTokenUtil.generateRefreshToken(user, deviceId);

        assertTrue(jwtTokenUtil.validateJwtToken(accessToken));
        assertTrue(jwtTokenUtil.validateJwtToken(refreshToken));
        assertTrue(jwtTokenUtil.isAccessToken(accessToken));
        assertFalse(jwtTokenUtil.isRefreshToken(accessToken));
        assertTrue(jwtTokenUtil.isRefreshToken(refreshToken));
        assertFalse(jwtTokenUtil.isAccessToken(refreshToken));
        assertEquals(user.getId().toString(), jwtTokenUtil.getSubjectFromJwtToken(refreshToken));
        assertEquals(deviceId, jwtTokenUtil.getLoginDeviceIdFromToken(refreshToken));
    }
}
