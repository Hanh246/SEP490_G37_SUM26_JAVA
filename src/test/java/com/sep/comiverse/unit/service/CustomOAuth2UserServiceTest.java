package com.sep.comiverse.unit.service;

import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AuthService;
import com.sep.comiverse.service.CustomOAuth2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private IUserRepository userRepository;
    @Mock private IRoleRepository roleRepository;
    @Mock private AuthService authService;
    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(userRepository, roleRepository, authService);
    }

    @Test
    void processOAuth2User_existingUser_linksVerifiedGoogleIdentity() {
        UserEntity existing = UserEntity.builder()
                .email("reader@example.com")
                .provider("GOOGLE")
                .providerId("old-sub")
                .avatarUrl("old.png")
                .build();
        OAuth2User oauth = oauthUser("reader@example.com", "Reader", "new-sub", "new.png");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(existing));
        when(authService.linkVerifiedGoogleIdentity(existing, "new-sub", "new.png")).thenReturn(existing);

        OAuth2User result = ReflectionTestUtils.invokeMethod(service, "processOAuth2User", oauth);

        assertSame(oauth, result);
        verify(authService).linkVerifiedGoogleIdentity(existing, "new-sub", "new.png");
        verify(userRepository, never()).save(existing);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void processOAuth2User_existingUser_doesNotEraseAvatarWhenGooglePictureMissing() {
        UserEntity existing = UserEntity.builder()
                .email("reader@example.com")
                .avatarUrl("keep.png")
                .build();
        OAuth2User oauth = oauthUser("reader@example.com", "Reader", "sub", null);
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(existing));
        when(authService.linkVerifiedGoogleIdentity(existing, "sub", null)).thenReturn(existing);

        ReflectionTestUtils.invokeMethod(service, "processOAuth2User", oauth);

        verify(authService).linkVerifiedGoogleIdentity(existing, "sub", null);
    }

    @Test
    void processOAuth2User_newUser_createsActiveGoogleReader() {
        RoleEntity reader = RoleEntity.builder().roleName("READER").build();
        OAuth2User oauth = oauthUser("new@example.com", "New Reader", "sub-1", "avatar.png");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("READER")).thenReturn(Optional.of(reader));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(service, "processOAuth2User", oauth);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("new@example.com", saved.getUsername());
        assertEquals("New Reader", saved.getFullName());
        assertEquals("GOOGLE", saved.getProvider());
        assertEquals("sub-1", saved.getProviderId());
        assertEquals("ACTIVE", saved.getStatus());
        assertSame(reader, saved.getRole());
        verifyNoInteractions(authService);
    }

    @Test
    void processOAuth2User_rejectsExplicitlyUnverifiedGoogleEmail() {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("email", "reader@example.com");
        attrs.put("name", "Reader");
        attrs.put("sub", "sub");
        attrs.put("email_verified", false);
        OAuth2User oauth = new DefaultOAuth2User(List.of(), attrs, "sub");

        assertThrows(
                org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "processOAuth2User", oauth)
        );

        verifyNoInteractions(userRepository, roleRepository, authService);
    }

    @Test
    void processOAuth2User_newUser_failsWhenDefaultReaderRoleMissing() {
        OAuth2User oauth = oauthUser("new@example.com", "New Reader", "sub-1", null);
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByRoleName("READER")).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "processOAuth2User", oauth)
        );

        assertTrue(error.getMessage().contains("Default role 'READER' not found"));
        verify(userRepository, never()).save(any());
    }

    private OAuth2User oauthUser(String email, String name, String sub, String picture) {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("email", email);
        attrs.put("name", name);
        attrs.put("sub", sub);
        if (picture != null) attrs.put("picture", picture);
        return new DefaultOAuth2User(List.of(), attrs, "sub");
    }
}
