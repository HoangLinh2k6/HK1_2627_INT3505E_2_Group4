package group4.backend.soa.service;

import group4.backend.soa.auth.AccessRules;
import group4.backend.soa.entity.User;
import group4.backend.soa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("User", id));
    }

    public User updateUser(Long id, User user) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("User", id));
        existingUser.setFullName(user.getFullName());
        existingUser.setPhone(user.getPhone());

        return userRepository.save(existingUser);
    }
}
