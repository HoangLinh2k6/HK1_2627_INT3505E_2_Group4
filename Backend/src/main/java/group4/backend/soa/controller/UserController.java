package group4.backend.soa.controller;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.auth.AuthController.Data;
import group4.backend.soa.auth.AuthUser;
import group4.backend.soa.entity.User;
import group4.backend.soa.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AccessRules access;

    @GetMapping
    public Data<AuthUser> getCurrentUser() {
        return new Data<>(AuthUser.from(userService.getUserById(access.userId())));
    }

    @PutMapping
    public Data<AuthUser> updateCurrentUser(@Valid @RequestBody Profile input) {
        User user = new User();
        user.setFullName(input.fullName().strip());
        user.setPhone(input.phone());
        return new Data<>(AuthUser.from(userService.updateUser(access.userId(), user)));
    }

    public record Profile(@NotBlank @Size(max = 255) String fullName,
                          @NotBlank @Pattern(regexp = "0[0-9]{9}") String phone) {}
}
