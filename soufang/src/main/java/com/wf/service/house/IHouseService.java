package com.wf.service.house;

import com.wf.base.HouseSubscribeStatus;
import com.wf.service.ServiceMultiResult;
import com.wf.service.ServiceResult;
import com.wf.web.dto.HouseDTO;
import com.wf.web.dto.HouseSubscribeDTO;
import com.wf.web.form.DatatableSearch;
import com.wf.web.form.HouseForm;
import com.wf.web.form.MapSearch;
import com.wf.web.form.RentSearch;
import org.springframework.data.util.Pair;

/**
 * 房屋管理服务接口
 */
public interface IHouseService {
    /**
     * 新增
     * @param houseForm
     * @return
     */
    ServiceResult<HouseDTO> save(HouseForm houseForm);

    ServiceMultiResult<HouseDTO> adminQuery(DatatableSearch searchBody);

    ServiceResult<HouseDTO> findCompleteOne(Long id);

    ServiceResult update(HouseForm houseForm);

    ServiceResult removePhoto(Long id);

    ServiceResult updateCover(Long coverId, Long targetId);

    ServiceResult addTag(Long houseId, String tag);

    ServiceResult removeTag(Long houseId, String tag);

    ServiceResult updateStatus(Long id, int value);

    ServiceMultiResult<HouseDTO> query(RentSearch rentSearch);

    ServiceMultiResult<HouseDTO> wholeMapQuery(MapSearch mapSearch);

    ServiceMultiResult<HouseDTO> boundMapQuery(MapSearch mapSearch);

    ServiceResult addSubscribeOrder(Long houseId);

    ServiceMultiResult<Pair<HouseDTO,HouseSubscribeDTO>> querySubscribeList(HouseSubscribeStatus of, int start, int size);
}
