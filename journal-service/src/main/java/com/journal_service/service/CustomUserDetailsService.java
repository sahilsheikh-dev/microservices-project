package com.journal_service.service;

import com.journal_service.modal.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final String adminEmail;
    private final String encodedAdminPassword;

    public CustomUserDetailsService(
            @Value("${admin.email}") String adminEmail,
            @Value("${admin.password}") String adminPassword
    ) {
        this.adminEmail = adminEmail;
        this.encodedAdminPassword = new BCryptPasswordEncoder().encode(adminPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (adminEmail.equalsIgnoreCase(username)) {
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

            return CustomUserDetails.builder()
                    .id(1L)
                    .email(adminEmail)
                    .password(encodedAdminPassword)
                    .authorities(authorities)
                    .build();
        }
        throw new UsernameNotFoundException("User not found with email: " + username);
    }
}
