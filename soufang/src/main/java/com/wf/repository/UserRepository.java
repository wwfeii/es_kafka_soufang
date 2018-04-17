package com.wf.repository;

import com.wf.entity.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User,Long> {
    User findByName(String userName);

    User findById(Long userId);
}
