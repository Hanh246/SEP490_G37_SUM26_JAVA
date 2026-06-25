package com.sep.comiverse.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenUtil jwtTokenUtil;

    @Value("${app.oauth2.authorizedRedirectUri}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // Tạo token từ email
        String token = jwtTokenUtil.generateToken(email);

        String targetUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("token", token)
                .build().toUriString(); // Chuyển hướng về React với token

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
