package com.wf.base;

import com.wf.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登陆相关  util
 */
public class LoginUserUtil {

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
}
