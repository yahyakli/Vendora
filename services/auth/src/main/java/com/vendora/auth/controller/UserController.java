package com.vendora.auth.controller;

import com.vendora.auth.entity.Role;
import com.vendora.auth.entity.User;
import com.vendora.auth.repository.RefreshTokenRepository;
import com.vendora.auth.repository.RoleRepository;
import com.vendora.auth.repository.UserRepository;
import com.vendora.auth.security.UserDetailsImpl;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/users", "/users"})
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder encoder;
    private final com.vendora.auth.service.MailService mailService;

    // --- User Profile Endpoints ---

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@RequestBody User userUpdate) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        
        user.setName(userUpdate.getName());
        user.setAvatarUrl(userUpdate.getAvatarUrl());
        
        userRepository.save(user);
        return ResponseEntity.ok("Profile updated successfully");
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (!encoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Error: Incorrect old password");
        }

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
        
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
        
        return ResponseEntity.ok("Password changed successfully");
    }

    // --- Admin Endpoints ---

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            Pageable pageable) {
        
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (search != null && !search.isEmpty()) {
                String likeSearch = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), likeSearch),
                    cb.like(cb.lower(root.get("email")), likeSearch)
                ));
            }
            
            if (role != null && !role.isEmpty()) {
                Join<User, Role> roleJoin = root.join("roles");
                predicates.add(cb.equal(roleJoin.get("name"), Role.ERole.valueOf(role.toUpperCase())));
            }
            
            if (!includeDeleted) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        return ResponseEntity.ok(userRepository.findAll(spec, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody List<String> roleNames) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(Role.ERole.valueOf(name.toUpperCase()))
                        .orElseThrow(() -> new RuntimeException("Error: Role " + name + " is not found.")))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        userRepository.save(user);
        return ResponseEntity.ok("User roles updated successfully");
    }

    @PutMapping("/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> banUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));
        user.setIsBanned(true);
        userRepository.save(user);
        return ResponseEntity.ok("User banned successfully");
    }

    @PutMapping("/{id}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unbanUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));
        user.setIsBanned(false);
        userRepository.save(user);
        return ResponseEntity.ok("User unbanned successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));
        user.setDeletedAt(LocalDateTime.now());
        user.setEnabled(false);
        userRepository.save(user);
        return ResponseEntity.ok("User soft-deleted successfully");
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserSessions(@PathVariable Long id) {
        return ResponseEntity.ok(refreshTokenRepository.findByUser_Id(id));
    }
}
