package be.kul.userservice.controller;

import be.kul.userservice.entity.User;
import be.kul.userservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping(path="/users")
public class UserController {
    //logger setup
    Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @PostMapping(path="/")
    public @ResponseBody User registerUser (@AuthenticationPrincipal Jwt principal, @RequestBody User user){
        String userId = principal.getClaimAsString("sub");
        user.setId(userId);

        return userService.registerUser(user);
    }

    @GetMapping(path="/")
    public @ResponseBody
    Optional<User> getUserById (@AuthenticationPrincipal Jwt principal){
        String userId = principal.getClaimAsString("sub");
        return userService.getUserById(userId);
    }


    @RequestMapping(path="/getId")
    public String test(@AuthenticationPrincipal Jwt principal) {
        return principal.getClaimAsString("sub");
    }
}