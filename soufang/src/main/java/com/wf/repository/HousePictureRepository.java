package com.wf.repository;

import java.util.List;

import com.wf.entity.HousePicture;
import org.springframework.data.repository.CrudRepository;

import com.wf.entity.HousePicture;

/**
 * Created by 瓦力.
 */
public interface HousePictureRepository extends CrudRepository<HousePicture, Long> {
    List<HousePicture> findAllByHouseId(Long id);
}
