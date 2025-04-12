package com.user_service.tests;

import com.user_service.controller.UserController;
import com.user_service.entity.User;

import com.user_service.service.KafkaProducerService;
import com.user_service.service.UserService;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@ExtendWith(MockitoExtension.class)
public class UserOperationsTests {

    @Mock
    private UserService userService;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private UserController userController;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .roles("USER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateUser_Success() {
        Mockito.when(userService.createUser(Mockito.any(User.class))).thenReturn(testUser);

        ResponseEntity<User> response = userController.createUser(testUser);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assertions.assertEquals(testUser, response.getBody());
        Mockito.verify(userService).createUser(testUser);
        Mockito.verify(kafkaProducerService).sendMessage("CREATE", "SUCCESS", "COMPLETED", testUser);
    }

    @Test
    void testCreateUser_Failure() {
        Mockito.when(userService.createUser(Mockito.any())).thenThrow(new RuntimeException("Create failed"));

        ResponseEntity<User> response = userController.createUser(testUser);

        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Mockito.verify(kafkaProducerService).sendMessage("CREATE", "FAILURE", "Create failed", testUser);
    }

    @Test
    void testGetAllUsers() {
        List<User> userList = List.of(testUser);
        Mockito.when(userService.getAllUsers()).thenReturn(userList);

        List<User> result = userController.getAllUsers();

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(testUser, result.get(0));
        Mockito.verify(userService).getAllUsers();
    }

    @Test
    void testGetUserById_Success() {
        Mockito.when(userService.getUserById(1L)).thenReturn(testUser);

        ResponseEntity<User> response = userController.getUserById(1L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(testUser, response.getBody());
        Mockito.verify(userService).getUserById(1L);
        Mockito.verify(kafkaProducerService).sendMessage("GET", "SUCCESS", "COMPLETED", testUser);
    }

    @Test
    void testGetUserById_NotFound() {
        Mockito.when(userService.getUserById(1L)).thenThrow(new ResourceNotFoundException("User not found"));

        ResponseEntity<User> response = userController.getUserById(1L);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Mockito.verify(kafkaProducerService).sendMessage(Mockito.eq("GET"), Mockito.eq("FAILURE"), Mockito.eq("User not found"), Mockito.any(User.class));
    }

    @Test
    void testUpdateUser_Success() {
        User updated = testUser;
        updated.setName("Updated Name");

        Mockito.when(userService.updateUser(Mockito.eq(1L), Mockito.any(User.class))).thenReturn(updated);

        ResponseEntity<User> response = userController.updateUser(1L, updated);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("Updated Name", Objects.requireNonNull(response.getBody()).getName());
        Mockito.verify(userService).updateUser(1L, updated);
        Mockito.verify(kafkaProducerService).sendMessage("UPDATE", "SUCCESS", "COMPLETED", updated);
    }

    @Test
    void testUpdateUser_Failure() {
        Mockito.when(userService.updateUser(Mockito.eq(1L), Mockito.any())).thenThrow(new RuntimeException("Update failed"));

        ResponseEntity<User> response = userController.updateUser(1L, testUser);

        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Mockito.verify(kafkaProducerService).sendMessage("UPDATE", "FAILURE", "Update failed", testUser);
    }

    @Test
    void testDeleteUser_Success() {
        Mockito.when(userService.deleteUser(1L)).thenReturn(testUser);

        ResponseEntity<User> response = userController.deleteUser(1L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(testUser, response.getBody());
        Mockito.verify(userService).deleteUser(1L);
        Mockito.verify(kafkaProducerService).sendMessage("DELETE", "SUCCESS", "COMPLETED", testUser);
    }

    @Test
    void testDeleteUser_NotFound() {
        Mockito.when(userService.deleteUser(1L)).thenThrow(new ResourceNotFoundException("User not found"));

        ResponseEntity<User> response = userController.deleteUser(1L);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Mockito.verify(kafkaProducerService).sendMessage(Mockito.eq("DELETE"), Mockito.eq("FAILURE"), Mockito.eq("User not found"), Mockito.any(User.class));
    }
}
