package group4.backend.soa.service.impl;

import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.User;
import group4.backend.soa.repository.UserRepository;
import group4.backend.soa.auth.AccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("User"));
    }

    @Override
    public User updateUser(Long id, User user) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> AccessRules.notFound("User"));
        existingUser.setFullName(user.getFullName());
        existingUser.setPhone(user.getPhone());

        return userRepository.save(existingUser);
    }
}
