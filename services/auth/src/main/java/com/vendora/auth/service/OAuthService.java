package com.vendora.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuthService {

    @Value("${vendora.oauth.google.client-id}")
    private String clientId;

    @Value("${vendora.oauth.google.client-secret}")
    private String clientSecret;

    @Value("${vendora.oauth.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${vendora.oauth.github.client-id}")
    private String githubClientId;

    @Value("${vendora.oauth.github.client-secret}")
    private String githubClientSecret;

    @Value("${vendora.oauth.github.redirect-uri}")
    private String githubRedirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getGoogleAuthUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + clientId +
                "&redirect_uri=" + googleRedirectUri +
                "&response_type=code" +
                "&scope=email profile openid" +
                "&access_type=offline";
    }

    public Map<String, Object> getGoogleAccessToken(String code) {
        String url = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("redirect_uri", googleRedirectUri);
        map.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        return response.getBody();
    }

    public Map<String, Object> getGoogleUserProfile(String accessToken) {
        String url = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return response.getBody();
    }

    // --- GitHub ---

    public String getGitHubAuthUrl() {
        return "https://github.com/login/oauth/authorize?" +
                "client_id=" + githubClientId +
                "&redirect_uri=" + githubRedirectUri +
                "&scope=user:email read:user";
    }

    public Map<String, Object> getGitHubAccessToken(String code) {
        String url = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", githubClientId);
        map.add("client_secret", githubClientSecret);
        map.add("redirect_uri", githubRedirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        return response.getBody();
    }

    public Map<String, Object> getGitHubUserProfile(String accessToken) {
        String url = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> profile = (Map<String, Object>) response.getBody();

        // If email is private, we need to fetch it from /user/emails
        if (profile != null && profile.get("email") == null) {
            String emailUrl = "https://api.github.com/user/emails";
            ResponseEntity<List> emailResponse = restTemplate.exchange(emailUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> emails = emailResponse.getBody();
            if (emails != null) {
                for (Map<String, Object> emailObj : emails) {
                    if ((Boolean) emailObj.get("primary") && (Boolean) emailObj.get("verified")) {
                        profile.put("email", emailObj.get("email"));
                        break;
                    }
                }
            }
        }

        return profile;
    }
}
