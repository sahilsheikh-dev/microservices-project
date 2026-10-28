package com.user_service.tests;

import com.user_service.dto.UserCreateRequest;
import com.user_service.dto.UserUpdateRequest;
import com.user_service.entity.User;
import com.user_service.exception.ConflictException;
import com.user_service.repository.UserRepository;
import com.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserOperationsTests {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    void createUser_ignoresSuppliedRoleAndHashesPassword() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.createUser(new UserCreateRequest(
                "Person", " PERSON@EXAMPLE.COM ", "password123"));

        assertThat(response.email()).isEqualTo("person@example.com");
        assertThat(response.roles()).isEqualTo("USER");
        verify(userRepository).save(argThat(user ->
                user.getRoles().equals("USER") && user.getPassword().equals("encoded")));
    }

    @Test
    void createUser_duplicateEmail_conflicts() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.createUser(
                new UserCreateRequest("Person", "person@example.com", "password123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_passwordAndRole_areEncodedAndNormalized() {
        User existing = User.builder().userId(10L).email("person@example.com")
                .password("old").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpassword")).thenReturn("encoded");
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateUser(10L, new UserUpdateRequest(null, null, "newpassword", "admin"));

        assertThat(existing.getPassword()).isEqualTo("encoded");
        assertThat(existing.getRoles()).isEqualTo("ADMIN");
    }
}
