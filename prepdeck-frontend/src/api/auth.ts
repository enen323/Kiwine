import request from './request';
import type {
  ApiResponse,
  RegisterRequest,
  LoginRequest,
  RefreshRequest,
  TokenResponse,
  UserVO,
} from '../types';

export const authApi = {
  register: (data: RegisterRequest) =>
    request.post<ApiResponse<TokenResponse>>('/auth/register', data),

  login: (data: LoginRequest) =>
    request.post<ApiResponse<TokenResponse>>('/auth/login', data),

  refresh: (data: RefreshRequest) =>
    request.post<ApiResponse<TokenResponse>>('/auth/refresh', data),

  logout: () =>
    request.post<ApiResponse<null>>('/auth/logout'),

  getMe: () =>
    request.get<ApiResponse<UserVO>>('/auth/me'),
};
