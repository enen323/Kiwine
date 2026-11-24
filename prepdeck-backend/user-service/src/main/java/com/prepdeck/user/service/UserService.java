package com.prepdeck.user.service;

import com.prepdeck.user.dto.request.UserUpdateRequest;
import com.prepdeck.user.dto.response.UserVO;

public interface UserService {

    UserVO getById(Long id);

    UserVO update(Long userId, UserUpdateRequest request);

    UserVO getCurrentUser();

}
