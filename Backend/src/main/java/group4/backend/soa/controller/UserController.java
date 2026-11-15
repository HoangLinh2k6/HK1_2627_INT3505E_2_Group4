package group4.backend.soa.controller;

import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
//Chưa hoàn thành
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor 
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<User> getCurrentUser(@RequestParam Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping
    public ResponseEntity<User> updateCurrentUser(@RequestParam Long id, @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);
        return ResponseEntity.ok(updatedUser);
    }
}
