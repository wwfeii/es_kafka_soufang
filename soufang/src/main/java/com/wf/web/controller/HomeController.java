package com.wf.web.controller;

import com.wf.base.ApiResponse;
import com.wf.base.LoginUserUtil;
import com.wf.service.ISmsService;
import com.wf.service.ServiceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {
    @Autowired
    private ISmsService smsService;

    @RequestMapping("/")
    public String goIndex(){
        return "index";
    }

    @RequestMapping("/404")
    public String goNotFoundPage(){
        return "404";
    }

    @RequestMapping("/403")
    public String goFTPage(){
        return "403";
    }

    @RequestMapping("/500")
    public String goFPage(){
        return "500";
    }

    @RequestMapping("/logout/page")
    public String goLogout(){
        return "logout";
    }
    @RequestMapping("/test")
    public String goTest()
    {
        return "test";
    }

    @GetMapping(value="sms/code")
    @ResponseBody
    public ApiResponse smsCode(@RequestParam("telephone") String telephone) {
        if(!LoginUserUtil.checkTelephone(telephone)){
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(), "请输入正确的手机号");
        }
        ServiceResult<String> result = smsService.sendSms(telephone);
        if (result.isSuccess()) {
            return ApiResponse.ofSuccess("");
        } else {
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(), result.getMessage());
        }
    }

}
