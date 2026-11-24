package com.prepdeck.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.common.constant.AuthConstant;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.DuplicateException;
import com.prepdeck.common.exception.ForbiddenException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.util.JwtProvider;
import com.prepdeck.common.util.TokenBlacklist;
import com.prepdeck.user.domain.UserEntity;
import com.prepdeck.user.dto.request.LoginRequest;
import com.prepdeck.user.dto.request.RefreshTokenRequest;
import com.prepdeck.user.dto.request.RegisterRequest;
import com.prepdeck.user.dto.response.LoginResponse;
import com.prepdeck.user.dto.response.UserVO;
import com.prepdeck.user.repository.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 *
 * <p>处理注册、登录、Token 刷新与登出逻辑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 1. 校验手机号唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getPhone, request.phone())
        );
        if (count > 0) {
            throw new DuplicateException(ErrorCode.USER_EXISTS);
        }

        // 2. 创建用户
        UserEntity user = new UserEntity();
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(1);
        userMapper.insert(user);
        log.info("用户注册成功: userId={}, phone={}", user.getId(), request.phone());

        // 3. 生成令牌
        String accessToken = generateAccessToken(user.getId());
        String refreshToken = generateRefreshToken(user.getId());

        return buildLoginResponse(accessToken, refreshToken, user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查找用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getPhone, request.phone())
        );
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadRequestException(ErrorCode.PASSWORD_ERROR);
        }

        // 3. 检查账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户登录成功: userId={}, phone={}", user.getId(), request.phone());

        // 5. 生成令牌
        String accessToken = generateAccessToken(user.getId());
        String refreshToken = generateRefreshToken(user.getId());

        return buildLoginResponse(accessToken, refreshToken, user);
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        // 1. 校验并解析刷新令牌
        Claims claims;
        try {
            claims = JwtProvider.getInstance().validateToken(request.refreshToken());
        } catch (ExpiredJwtException e) {
            throw new BadRequestException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new BadRequestException(ErrorCode.TOKEN_INVALID);
        }

        // 2. 提取用户信息
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);

        // 3. 令牌轮转：生成新令牌对
        String newAccessToken = JwtProvider.getInstance().createAccessToken(userId, role);
        String newRefreshToken = JwtProvider.getInstance().createRefreshToken(userId, role);

        // 4. 获取用户信息
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        log.info("Token 刷新成功: userId={}", userId);
        return buildLoginResponse(newAccessToken, newRefreshToken, user);
    }

    @Override
    public void logout(Long userId, String refreshToken) {
        String jti = JwtProvider.getInstance().getJti(refreshToken);
        if (jti != null) {
            String key = TokenBlacklist.blacklistKey(jti);
            long ttl = AuthConstant.REFRESH_TOKEN_EXPIRE_MS;
            stringRedisTemplate.opsForValue().set(key, "1", ttl, java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("Token 已加入黑名单: userId={}, jti={}", userId, jti);
        }
        log.info("用户登出: userId={}", userId);
    }

    // ========== 私有辅助方法 ==========

    private String generateAccessToken(Long userId) {
        return JwtProvider.getInstance().createAccessToken(userId, "USER");
    }

    private String generateRefreshToken(Long userId) {
        return JwtProvider.getInstance().createRefreshToken(userId, "USER");
    }

    private LoginResponse buildLoginResponse(String accessToken, String refreshToken, UserEntity user) {
        return new LoginResponse(
                accessToken,
                refreshToken,
                AuthConstant.ACCESS_TOKEN_EXPIRE_MS / 1000,
                toUserVO(user)
        );
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
