package com.wf.service;

import com.wf.entity.User;
import com.wf.web.dto.UserDTO;

public interface IUserService {
    User findUserByName(String userName);

    ServiceResult<UserDTO> findById(Long adminId);
}
