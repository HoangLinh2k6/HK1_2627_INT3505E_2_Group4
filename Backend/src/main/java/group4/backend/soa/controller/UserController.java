package group4.backend.soa.controller;

import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<User> getCurrentUser(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String email) {
        if (id != null) {
            return ResponseEntity.ok(userService.getUserById(id));
        }
        if (email != null && !email.isBlank()) {
            return ResponseEntity.ok(userService.getUserByEmail(email));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id or email is required");
    }

    @PostMapping
    public ResponseEntity<User> createOrGetUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.findOrCreateUser(user));
    }

    @PutMapping
    public ResponseEntity<User> updateCurrentUser(@RequestParam Long id, @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);
    }
}
