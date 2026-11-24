package com.prepdeck.user.service;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.user.domain.UserEntity;
import com.prepdeck.user.dto.request.UserUpdateRequest;
import com.prepdeck.user.dto.response.UserVO;
import com.prepdeck.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserVO getById(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserVO(user);
    }

    @Override
    public UserVO update(Long userId, UserUpdateRequest request) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.nickname() != null) user.setNickname(request.nickname());
        if (request.targetPosition() != null) user.setTargetPosition(request.targetPosition());
        if (request.targetCompany() != null) user.setTargetCompany(request.targetCompany());

        userMapper.updateById(user);
        log.info("User updated: userId={}", userId);
        return toUserVO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO getCurrentUser() {
        Long userId = SecurityContext.getCurrentUserId();
        return getById(userId);
    }

    private UserVO toUserVO(UserEntity entity) {
        return new UserVO(
                entity.getId(),
                entity.getPhone(),
                entity.getNickname(),
                entity.getAvatarUrl(),
                entity.getTargetPosition(),
                entity.getTargetCompany(),
                entity.getCreateTime()
        );
    }
}
