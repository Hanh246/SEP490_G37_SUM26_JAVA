package com.sep.comiverse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final AuthService authService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(oAuth2User);
    }

    private OAuth2User processOAuth2User(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String googleId = oAuth2User.getAttribute("sub");
        String picture = oAuth2User.getAttribute("picture");
        Object emailVerified = oAuth2User.getAttribute("email_verified");

        if (email == null || email.isBlank() || googleId == null || googleId.isBlank()
                || (emailVerified != null
                && !Boolean.TRUE.equals(emailVerified)
                && !"true".equalsIgnoreCase(String.valueOf(emailVerified)))) {
            throw new OAuth2AuthenticationException("Google account email is not verified");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        Optional<UserEntity> userOptional = userRepository.findByEmail(email);
        UserEntity user;
        if (userOptional.isPresent()) {
            user = authService.linkVerifiedGoogleIdentity(userOptional.get(), googleId, picture);
        } else {
            RoleEntity userRole = roleRepository.findByRoleName("READER")
                    .orElseThrow(() -> new RuntimeException("Default role 'READER' not found"));
            user = UserEntity.builder()
                    .email(email)
                    .fullName(name)
                    .username(email)
                    .provider("GOOGLE")
                    .providerId(googleId)
                    .avatarUrl(picture)
                    .status("ACTIVE")
                    .role(userRole)
                    .build();
            userRepository.save(user);
        }
        return oAuth2User;
    }
}
