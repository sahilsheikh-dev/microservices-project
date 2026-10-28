package com.user_service.tests;

import com.user_service.dto.UserCreateRequest;
import com.user_service.dto.UserUpdateRequest;
import com.user_service.entity.User;
import com.user_service.exception.ConflictException;
import com.user_service.repository.UserRepository;
import com.user_service.service.UserService;
import com.user_service.service.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
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
    @Mock
    private UserEventPublisher eventPublisher;
    @InjectMocks
    private UserService userService;

    @Test
    void createUser_assignsUserRoleAndHashesPassword() {
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

    @Test
    void createBootstrapAdmin_validConfiguration_createsAdministratorOnlyOnce() {
        when(userRepository.existsByRoles("ADMIN")).thenReturn(false, true);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User first = userService.createBootstrapAdmin("ADMIN@example.com", "strong-password");
        User second = userService.createBootstrapAdmin("admin@example.com", "strong-password");

        assertThat(first.getRoles()).isEqualTo("ADMIN");
        assertThat(first.getPassword()).isEqualTo("encoded");
        assertThat(second).isNull();
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void createBootstrapAdmin_blankValues_skipsBootstrap() {
        assertThat(userService.createBootstrapAdmin(" ", "password")).isNull();
        assertThat(userService.createBootstrapAdmin("admin@example.com", " ")).isNull();

        verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void createBootstrapAdmin_existingAdministrator_skipsCreation() {
        when(userRepository.existsByRoles("ADMIN")).thenReturn(true);

        assertThat(userService.createBootstrapAdmin("admin@example.com", "strong-password")).isNull();

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void updateUser_passwordProvided_persistsEncodedPassword() {
        User existing = User.builder().userId(10L).email("person@example.com")
                .password("old").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpassword")).thenReturn("encoded");
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateUser(10L, new UserUpdateRequest("x", null, "newpassword", null));

        assertThat(existing.getPassword()).isEqualTo("encoded");
        verify(passwordEncoder).encode("newpassword");
    }

    @Test
    void updateUser_failureEvent_includesNoPasswordValue() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(
                10L, new UserUpdateRequest(null, null, "newpassword", null)))
                .isInstanceOf(com.user_service.exception.ResourceNotFoundException.class);

        verify(eventPublisher).publish("UPDATE", "FAILURE", "Internal error", 10L, null, null, null);
    }

    @Test
    void getAllUsers_pageRequested_returnsRepositoryPage() {
        var pageable = PageRequest.of(0, 10);
        Page<User> expected = new PageImpl<>(List.of(User.builder().userId(10L).build()));
        when(userRepository.findAll(pageable)).thenReturn(expected);

        assertThat(userService.getAllUsers(pageable)).isSameAs(expected);
    }

    @Test
    void getUserById_missingUser_throwsNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(404L))
                .isInstanceOf(com.user_service.exception.ResourceNotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void updateUser_emailProvided_normalizesAndPersistsEmail() {
        User existing = User.builder().userId(10L).email("person@example.com")
                .password("encoded").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("updated@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateUser(10L, new UserUpdateRequest(null, " UPDATED@EXAMPLE.COM ", null, null));

        assertThat(existing.getEmail()).isEqualTo("updated@example.com");
        verify(userRepository).save(existing);
    }

    @Test
    void updateUser_duplicateEmail_conflictsAndPublishesFailure() {
        User existing = User.builder().userId(10L).email("person@example.com").roles("USER").build();
        User duplicate = User.builder().userId(11L).email("other@example.com").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> userService.updateUser(
                10L, new UserUpdateRequest(null, "other@example.com", null, null)))
                .isInstanceOf(ConflictException.class);

        verify(eventPublisher).publish("UPDATE", "FAILURE", "Internal error", 10L,
                null, "other@example.com", null);
    }

    @Test
    void updateUser_invalidRole_throwsBadRequestAndPublishesFailure() {
        User existing = User.builder().userId(10L).email("person@example.com").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateUser(
                10L, new UserUpdateRequest(null, null, null, "USER,ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role must be ADMIN or USER.");

        verify(eventPublisher).publish("UPDATE", "FAILURE", "Internal error", 10L, null, null, null);
    }

    @Test
    void deleteUser_existingUser_deletesAndPublishesSuccess() {
        User existing = User.builder().userId(10L).name("Person")
                .email("person@example.com").roles("USER").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));

        assertThat(userService.deleteUser(10L)).isSameAs(existing);

        verify(userRepository).deleteById(10L);
        verify(eventPublisher).publish("DELETE", "SUCCESS", "User deleted", 10L,
                "Person", "person@example.com", "USER");
    }

    @Test
    void deleteUser_missingUser_publishesFailureAndRethrows() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(10L))
                .isInstanceOf(com.user_service.exception.ResourceNotFoundException.class);

        verify(userRepository, never()).deleteById(10L);
        verify(eventPublisher).publish("DELETE", "FAILURE", "Internal error", 10L,
                null, null, null);
    }
}
