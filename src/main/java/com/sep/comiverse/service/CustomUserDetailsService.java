package com.sep.comiverse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final IUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Load by username or email
        UserEntity user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + username));

        if ("INACTIVE".equals(user.getStatus())) {
            throw new CustomException(401, "Your account has been locked.", HttpStatus.UNAUTHORIZED);
        }

        return new UserPrincipal(user);
    }

    public UserDetails loadUserById(java.util.UUID id) throws UsernameNotFoundException {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        if ("INACTIVE".equals(user.getStatus())) {
            throw new CustomException(401, "Your account has been locked.", HttpStatus.UNAUTHORIZED);
        }

        return new UserPrincipal(user);
    }
}
