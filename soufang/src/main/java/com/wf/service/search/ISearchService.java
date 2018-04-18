package com.wf.service.search;

import com.wf.service.ServiceMultiResult;
import com.wf.web.form.RentSearch;

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
}
