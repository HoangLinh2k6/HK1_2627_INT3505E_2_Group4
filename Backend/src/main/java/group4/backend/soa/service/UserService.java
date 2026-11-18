package group4.backend.soa.service;

import group4.backend.soa.entity.User;

public interface UserService {
    User getUserById(Long id);
    User updateUser(Long id, User user);
}
