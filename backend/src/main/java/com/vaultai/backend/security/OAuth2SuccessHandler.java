package com.vaultai.backend.security;

import com.vaultai.backend.entity.User;
import com.vaultai.backend.repository.UserRepository;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        Object idAttr = oauth2User.getAttribute("id");
        String githubId = idAttr != null ? String.valueOf(idAttr) : null;

        String username = oauth2User.getAttribute("login");
        String email = oauth2User.getAttribute("email");
        String avatarUrl = oauth2User.getAttribute("avatar_url");

        String accessToken = null;
        try {
            OAuth2AuthorizedClient client = authorizedClientService
                    .loadAuthorizedClient("github", authentication.getName());
            if (client != null) {
                accessToken = client.getAccessToken().getTokenValue();
            }
        } catch (Exception e) {
            logger.warn("Could not load authorized client: {}", e.getMessage());
        }

        final String finalAccessToken = accessToken;

        User user = userRepository.findByGithubId(githubId)
                .map(existing -> {
                    if (finalAccessToken != null)
                        existing.setGithubAccessToken(finalAccessToken);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .githubId(githubId)
                                .username(username)
                                .email(email)
                                .avatarUrl(avatarUrl)
                                .githubAccessToken(finalAccessToken)
                                .build()
                ));

        String token = jwtUtil.generateToken(user.getUsername(), user.getGithubId());
        response.sendRedirect(frontendUrl + "/auth/callback?token=" + token);
    }
}
