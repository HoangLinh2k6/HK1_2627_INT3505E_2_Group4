package group4.backend.soa.service;

import group4.backend.soa.entity.User;

public interface UserService {
    User createUser(User user);
    User getUserById(Long id);
    User getUserByEmail(String email);
    User findOrCreateUser(User user);
    User updateUser(Long id, User user);
}
