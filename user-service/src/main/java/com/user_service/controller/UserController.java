package com.user_service.controller;

import com.user_service.service.KafkaProducerService;
import com.user_service.entity.User;
import com.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final KafkaProducerService kafkaProducerService;

    @PostMapping("")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return handleRequest("CREATE", user, () -> userService.createUser(user), HttpStatus.CREATED, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return handleRequest("GET", User.builder().userId(id).build(), () -> userService.getUserById(id), HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        return handleRequest("UPDATE", user, () -> userService.updateUser(id, user), HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<User> deleteUser(@PathVariable Long id) {
        return handleRequest("DELETE", User.builder().userId(id).build(), () -> userService.deleteUser(id), HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<User> handleRequest(String action, User inputUser, Supplier<User> operation, HttpStatus successStatus, HttpStatus errorStatus) {
        try {
            User result = operation.get();
            kafkaProducerService.sendMessage(action, "SUCCESS", "COMPLETED", result);
            return new ResponseEntity<>(result, successStatus);
        } catch (Exception ex) {
            kafkaProducerService.sendMessage(action, "FAILURE", ex.getMessage(), inputUser);
            return new ResponseEntity<>(errorStatus);
        }
    }

}
