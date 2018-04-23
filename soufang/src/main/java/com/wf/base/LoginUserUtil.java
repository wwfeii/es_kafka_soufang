package com.wf.base;

import com.wf.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Pattern;

/**
 * 登陆相关  util
 */
public class LoginUserUtil {
    private static final String PHONE_REGEX = "^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(18[0,5-9]))\\d{8}$";
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public static User load(){
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(obj != null && obj instanceof User){
            return (User) obj;
        }
        return null;
    }

    public static Long getLoginUserId(){
        User user = load();
        if(user == null){
            return -1L;
        }
        return user.getId();
    }
    /*
     * @param target 目标号码
     * @return 如果是手机号码 返回true; 反之,返回false
     */
    public static boolean checkTelephone(String target) {
        return PHONE_PATTERN.matcher(target).matches();
    }
    /**
     * 验证一般的英文邮箱
     * @param target 目标邮箱
     * @return 如果符合邮箱规则 返回true; 反之,返回false
     */
    public static boolean checkEmail(String target) {
        return EMAIL_PATTERN.matcher(target).matches();
    }
}
