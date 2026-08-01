package com.sep.comiverse.unit.service;

import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private IUserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadByUsernameSupportsEmailAndExposesStoredRoleAuthority() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByUsernameOrEmail("reader@example.com", "reader@example.com"))
                .thenReturn(Optional.of(user));

        UserPrincipal principal = assertInstanceOf(
                UserPrincipal.class,
                service.loadUserByUsername("reader@example.com")
        );

        assertEquals(user.getId(), principal.getId());
        assertEquals("READER", principal.getRole());
        assertEquals("READER", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadByUsernameRejectsInactiveAccount() {
        UserEntity user = user("INACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.loadUserByUsername("reader")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
        assertEquals("Your account has been locked.", error.getMessage());
    }

    @Test
    void loadByIdReturnsPrincipalForActiveAccount() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByIdWithRole(user.getId())).thenReturn(Optional.of(user));

        UserPrincipal principal = assertInstanceOf(UserPrincipal.class, service.loadUserById(user.getId()));

        assertEquals("reader", principal.getUsername());
        assertEquals("reader@example.com", principal.getEmail());
    }

    @Test
    void loadByIdUsesSpringSecurityNotFoundExceptionForMissingAccount() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.empty());

        UsernameNotFoundException error = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserById(userId)
        );

        assertEquals("User not found with id: " + userId, error.getMessage());
    }

    private UserEntity user(String status) {
        UserEntity user = UserEntity.builder()
                .username("reader")
                .password("encoded-password")
                .fullName("Reader One")
                .email("reader@example.com")
                .role(RoleEntity.builder().roleName("READER").build())
                .status(status)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
