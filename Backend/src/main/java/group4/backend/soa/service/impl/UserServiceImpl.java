package group4.backend.soa.service.impl;

import group4.backend.soa.service.UserService;
import group4.backend.soa.entity.User;
import group4.backend.soa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;


}
