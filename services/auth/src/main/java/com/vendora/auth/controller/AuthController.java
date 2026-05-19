package com.vendora.auth.controller;

import com.vendora.auth.dto.request.LoginRequest;
import com.vendora.auth.dto.request.RegisterRequest;
import com.vendora.auth.dto.request.TokenRefreshRequest;
import com.vendora.auth.dto.response.JwtResponse;
import com.vendora.auth.entity.PasswordResetToken;
import com.vendora.auth.entity.VerificationToken;
import com.vendora.auth.entity.RefreshToken;
import com.vendora.auth.entity.Role;
import com.vendora.auth.entity.User;
import com.vendora.auth.repository.PasswordResetTokenRepository;
import com.vendora.auth.repository.VerificationTokenRepository;
import com.vendora.auth.repository.RoleRepository;
import com.vendora.auth.repository.UserRepository;
import com.vendora.auth.repository.OAuthAccountRepository;
import com.vendora.auth.security.UserDetailsImpl;
import com.vendora.auth.security.jwt.JwtUtils;
import com.vendora.auth.service.MailService;
import com.vendora.auth.service.OAuthService;
import com.vendora.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final OAuthService oAuthService;
    private final OAuthAccountRepository oAuthAccountRepository;

    @GetMapping("/oauth/google")
    public void redirectToGoogle(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect(oAuthService.getGoogleAuthUrl());
    }

    @GetMapping("/oauth/google/callback")
    @Transactional
    public ResponseEntity<?> googleCallback(@RequestParam String code) {
        try {
            // 1. Exchange code for access token
            Map<String, Object> tokenResponse = oAuthService.getGoogleAccessToken(code);
            String accessToken = (String) tokenResponse.get("access_token");

            // 2. Fetch user profile
            Map<String, Object> profile = oAuthService.getGoogleUserProfile(accessToken);
            String email = (String) profile.get("email");
            String name = (String) profile.get("name");
            String providerId = (String) profile.get("sub");
            String picture = (String) profile.get("picture");

            // 3. Find or create user
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = User.builder()
                        .name(name)
                        .email(email)
                        .password(encoder.encode(UUID.randomUUID().toString()))
                        .enabled(true)
                        .avatarUrl(picture)
                        .build();

                Role buyerRole = roleRepository.findByName(Role.ERole.BUYER)
                        .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                newUser.getRoles().add(buyerRole);
                return userRepository.save(newUser);
            });

            // 4. Ensure OAuth Account exists
            oAuthAccountRepository.findByProviderAndProviderId("google", providerId)
                    .orElseGet(() -> {
                        OAuthAccount account = OAuthAccount.builder()
                                .user(user)
                                .provider("google")
                                .providerId(providerId)
                                .build();
                        return oAuthAccountRepository.save(account);
                    });

            // 5. Generate JWT
            String jwt = jwtUtils.generateTokenFromUsername(user.getEmail());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(JwtResponse.builder()
                    .token(jwt)
                    .refreshToken(refreshToken.getToken())
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .roles(roles)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: Google OAuth failed - " + e.getMessage());
        }
    }
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication, loginRequest.isRememberMe());

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

            return ResponseEntity.ok(JwtResponse.builder()
                    .token(jwt)
                    .refreshToken(refreshToken.getToken())
                    .id(userDetails.getId())
                    .name(userDetails.getName())
                    .email(userDetails.getEmail())
                    .roles(roles)
                    .build());
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(401).body("Error: Email not verified. Please check your email.");
        } catch (org.springframework.security.authentication.LockedException e) {
            return ResponseEntity.status(401).body("Error: Your account has been banned.");
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(401).body("Error: Invalid email or password.");
        }
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        User user = User.builder()
                .name(signUpRequest.getName())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .enabled(false) // User is disabled until email verification
                .build();

        Set<Role> roles = new HashSet<>();
        Role buyerRole = roleRepository.findByName(Role.ERole.BUYER)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        roles.add(buyerRole);

        user.setRoles(roles);
        userRepository.save(user);

        // Generate verification token
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(code)
                .expiryDate(Instant.now().plusSeconds(900)) // 15 minutes
                .build();
        
        verificationTokenRepository.save(verificationToken);
        
        mailService.sendVerificationEmail(user.getEmail(), user.getName(), code);

        return ResponseEntity.ok("User registered successfully! Please check your email to verify your account.");
    }

    @PostMapping("/verify-email")
    @Transactional
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (verificationToken.getExpiryDate().isBefore(Instant.now())) {
            verificationTokenRepository.delete(verificationToken);
            throw new RuntimeException("Verification token expired");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        
        verificationTokenRepository.delete(verificationToken);
        
        mailService.sendWelcomeEmail(user.getEmail(), user.getName());
        
        return ResponseEntity.ok("Email verified successfully! You can now log in.");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateTokenFromUsername(user.getEmail());
                    return ResponseEntity.ok(Map.of("accessToken", token, "refreshToken", requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            String jwt = headerAuth.substring(7);
            
            // Add to Redis blacklist
            try {
                Date expirationDate = jwtUtils.getExpirationDateFromJwtToken(jwt);
                long ttl = expirationDate.getTime() - System.currentTimeMillis();
                
                if (ttl > 0) {
                    redisTemplate.opsForValue().set("blacklist:" + jwt, "1", ttl, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                // Token might be malformed or already expired
            }
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = userDetails.getId();
        refreshTokenService.deleteByUserId(userId);
        return ResponseEntity.ok("Log out successful!");
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        if (jwtUtils.validateJwtToken(token)) {
            String username = jwtUtils.getUserNameFromJwtToken(token);
            User user = userRepository.findByEmail(username).orElseThrow();
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "userId", user.getId(),
                "role", user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList())
            ));
        }
        return ResponseEntity.status(401).body(Map.of("valid", false));
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        String code = String.format("%06d", new Random().nextInt(999999));
        
        passwordResetTokenRepository.deleteByUser(user);
        
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(code)
                .expiryDate(Instant.now().plusSeconds(600)) // 10 minutes
                .build();
        
        passwordResetTokenRepository.save(resetToken);
        
        mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), code);
        
        return ResponseEntity.ok("Reset code sent to your email.");
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
        
        passwordResetTokenRepository.delete(resetToken);
        
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
        
        return ResponseEntity.ok("Password reset successfully.");
    }
}
