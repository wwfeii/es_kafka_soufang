package com.wf.service.search;

import com.wf.service.ServiceMultiResult;
import com.wf.service.ServiceResult;
import com.wf.web.form.RentSearch;

import java.util.List;

public interface ISearchService {

    /**
     * 索引目标房源
     * @param houseId
     */
    void index(Long houseId);

    /**
     * 移除房源索引
     * @param houseId
     */
    void remove(Long houseId);

    //通过es查询
    ServiceMultiResult<Long> query(RentSearch rentSearch);

    /**
     * 根据内容 自动补全
     * @param prefix
     * @return
     */
    ServiceResult<List<String>> suggest(String prefix);

    /**
     * 聚合查询 小区出租数量
     * @param enName
     * @param enName1
     * @param district
     * @return
     */
    ServiceResult<Long> aggregateDistrictHouse(String enName, String enName1, String district);

    /**
     * 聚合查询 不同区域 的房屋出租量
     * @param cityEnName
     * @return
     */
    ServiceMultiResult<HouseBucketDTO> mapAggregate(String cityEnName);

    ServiceMultiResult<Long> mapQuery(String cityEnName, String orderBy, String orderDirection, int start, int size);

}
