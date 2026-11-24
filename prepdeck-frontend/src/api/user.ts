import request from './request';
import type { ApiResponse, UserVO, UpdateProfileRequest, UserAISettings } from '../types';

export const userApi = {
  getUser: (id: string | number) =>
    request.get<ApiResponse<UserVO>>(`/users/${id}`),

  updateProfile: (data: UpdateProfileRequest) =>
    request.put<ApiResponse<UserVO>>('/users/profile', data),

  getMe: () =>
    request.get<ApiResponse<UserVO>>('/users/me'),

  // 保存 AI 设置（provider/baseUrl/model）。apiKey 不传、不落库
  getSettings: () =>
    request.get<ApiResponse<UserAISettings>>('/users/settings'),

  updateSettings: (data: UserAISettings) =>
    request.put<ApiResponse<UserAISettings>>('/users/settings', data),
};
