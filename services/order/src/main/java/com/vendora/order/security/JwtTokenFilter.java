package com.vendora.order.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                Claims claims = jwtUtils.getClaims(jwt);
                String username = claims.getSubject();
                Long userId = null;

                Object userIdObj = claims.get("userId");
                if (userIdObj == null) {
                    userIdObj = claims.get("user_id");
                }
                if (userIdObj != null) {
                    userId = Long.valueOf(userIdObj.toString());
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                Object rolesObj = claims.get("roles");
                if (rolesObj instanceof List<?> roleList) {
                    for (Object r : roleList) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString().replace("ROLE_", "")));
                    }
                } else if (claims.get("role") != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.get("role").toString().replace("ROLE_", "")));
                } else {
                    authorities.add(new SimpleGrantedAuthority("ROLE_BUYER"));
                }

                UserPrincipal principal = new UserPrincipal(userId, username, authorities);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("user_id", userId);
                request.setAttribute("userId", userId);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
