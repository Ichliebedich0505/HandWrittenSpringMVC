package service.impl;

import annotation.Service;

@Service
public class UserServiceImpl implements service.impl.UserService {

    public String getUserName() {
        return "marcuspeng";
    }
}
