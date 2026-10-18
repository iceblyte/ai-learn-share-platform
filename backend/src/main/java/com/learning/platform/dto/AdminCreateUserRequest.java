package com.learning.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台创建用户请求：不传密码，统一使用系统默认密码，
 * 由用户首次登录后自行修改。
 */
@Data
public class AdminCreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度3-20位")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "用户名仅支持字母、数字、下划线和短横线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 可选，缺省取用户名 */
    @Size(max = 30, message = "昵称最长30位")
    private String nickname;

    /** 可选，缺省 USER；允许值 USER/PUBLISHER/ADMIN */
    private String role;
}
