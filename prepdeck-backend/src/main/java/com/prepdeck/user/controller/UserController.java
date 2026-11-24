package com.prepdeck.user.controller;

import com.prepdeck.common.exception.ForbiddenException;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.user.dto.request.UserUpdateRequest;
import com.prepdeck.user.dto.response.UserVO;
import com.prepdeck.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        if (!userId.equals(id)) {
            throw new ForbiddenException("无权查看其他用户信息");
        }
        return Result.success(userService.getById(id));
    }

    @PutMapping("/profile")
    public Result<UserVO> update(@Valid @RequestBody UserUpdateRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(userService.update(userId, request));
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }
}
