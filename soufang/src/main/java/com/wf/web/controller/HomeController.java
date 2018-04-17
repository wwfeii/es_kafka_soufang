package com.wf.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

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

}
