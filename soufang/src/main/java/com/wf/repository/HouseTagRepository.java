package com.wf.repository;

import java.util.List;

import com.wf.entity.HouseTag;
import org.springframework.data.repository.CrudRepository;

import com.wf.entity.HouseTag;

/**
 * Created by 瓦力.
 */
public interface HouseTagRepository extends CrudRepository<HouseTag, Long> {
    HouseTag findByNameAndHouseId(String name, Long houseId);

    List<HouseTag> findAllByHouseId(Long id);

    List<HouseTag> findAllByHouseIdIn(List<Long> houseIds);
}