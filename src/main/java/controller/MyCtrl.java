package controller;

import annotation.Controller;
import annotation.Qualifier;
import annotation.RequestMapping;
import service.impl.UserService;

@Controller
@RequestMapping("/user")
public class MyCtrl {

    @Qualifier("userService")
    private UserService userService;

    @RequestMapping("/getname")
    public void indexM(){

        System.out.println(userService.getUserName());
    }
}
