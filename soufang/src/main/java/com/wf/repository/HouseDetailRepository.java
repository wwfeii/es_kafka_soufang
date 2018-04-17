package com.wf.repository;

import java.util.List;

import com.wf.entity.HouseDetail;
import org.springframework.data.repository.CrudRepository;

import com.wf.entity.HouseDetail;

/**
 * Created by 瓦力.
 */
public interface HouseDetailRepository extends CrudRepository<HouseDetail, Long>{
    HouseDetail findByHouseId(Long houseId);

    List<HouseDetail> findAllByHouseIdIn(List<Long> houseIds);
}
