package com.wf.service.house;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wf.entity.Subway;
import com.wf.entity.SubwayStation;
import com.wf.entity.SupportAddress;
import com.wf.repository.SubwayRepository;
import com.wf.repository.SubwayStationRepository;
import com.wf.repository.SupportAddressRepository;
import com.wf.service.ServiceMultiResult;
import com.wf.service.ServiceResult;
import com.wf.service.search.BaiduMapLocation;
import com.wf.web.dto.SubwayDTO;
import com.wf.web.dto.SubwayStationDTO;
import com.wf.web.dto.SupportAddressDTO;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Created by 瓦力.
 */
@Service
public class AddressServiceImpl implements IAddressService {
    private Logger logger = LoggerFactory.getLogger(AddressServiceImpl.class);
    @Autowired
    private SupportAddressRepository supportAddressRepository;

    @Autowired
    private SubwayRepository subwayRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private SubwayStationRepository subwayStationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BAIDU_MAP_KEY = "6QtSF673D1pYl3eQkEXfwp8ZgsQpB77U";

    private static final String BAIDU_MAP_GEOCONV_API = "http://api.map.baidu.com/geocoder/v2/?";

//    /**
//     * POI数据管理接口
//     */
//    private static final String LBS_CREATE_API = "http://api.map.baidu.com/geodata/v3/poi/create";
//
//    private static final String LBS_QUERY_API = "http://api.map.baidu.com/geodata/v3/poi/list?";
//
//    private static final String LBS_UPDATE_API = "http://api.map.baidu.com/geodata/v3/poi/update";
//
//    private static final String LBS_DELETE_API = "http://api.map.baidu.com/geodata/v3/poi/delete";
//
//    private static final Logger logger = LoggerFactory.getLogger(IAddressService.class);

    @Override
    public ServiceMultiResult<SupportAddressDTO> findAllCities() {
        List<SupportAddress> addresses = supportAddressRepository.findAllByLevel(SupportAddress.Level.CITY.getValue());
        List<SupportAddressDTO> addressDTOS = new ArrayList<>();
        for (SupportAddress supportAddress : addresses) {
            SupportAddressDTO target = modelMapper.map(supportAddress, SupportAddressDTO.class);
            addressDTOS.add(target);
        }

        return new ServiceMultiResult<>(addressDTOS.size(), addressDTOS);
    }

    @Override
    public Map<SupportAddress.Level, SupportAddressDTO> findCityAndRegion(String cityEnName, String regionEnName) {
        Map<SupportAddress.Level, SupportAddressDTO> result = new HashMap<>();

        SupportAddress city = supportAddressRepository.findByEnNameAndLevel(cityEnName, SupportAddress.Level.CITY
                .getValue());
        SupportAddress region = supportAddressRepository.findByEnNameAndBelongTo(regionEnName, city.getEnName());

        result.put(SupportAddress.Level.CITY, modelMapper.map(city, SupportAddressDTO.class));
        result.put(SupportAddress.Level.REGION, modelMapper.map(region, SupportAddressDTO.class));
        return result;
    }

    @Override
    public ServiceMultiResult findAllRegionsByCityName(String cityName) {
        if (cityName == null) {
            return new ServiceMultiResult<>(0, null);
        }

        List<SupportAddressDTO> result = new ArrayList<>();

        List<SupportAddress> regions = supportAddressRepository.findAllByLevelAndBelongTo(SupportAddress.Level.REGION
                .getValue(), cityName);
        for (SupportAddress region : regions) {
            result.add(modelMapper.map(region, SupportAddressDTO.class));
        }
        return new ServiceMultiResult<>(regions.size(), result);
    }

    @Override
    public List<SubwayDTO> findAllSubwayByCity(String cityEnName) {
        List<SubwayDTO> result = new ArrayList<>();
        List<Subway> subways = subwayRepository.findAllByCityEnName(cityEnName);
        if (subways.isEmpty()) {
            return result;
        }

        subways.forEach(subway -> result.add(modelMapper.map(subway, SubwayDTO.class)));
        return result;
    }

    @Override
    public List<SubwayStationDTO> findAllStationBySubway(Long subwayId) {
        List<SubwayStationDTO> result = new ArrayList<>();
        List<SubwayStation> stations = subwayStationRepository.findAllBySubwayId(subwayId);
        if (stations.isEmpty()) {
            return result;
        }

        stations.forEach(station -> result.add(modelMapper.map(station, SubwayStationDTO.class)));
        return result;
    }

    @Override
    public ServiceResult<SubwayDTO> findSubway(Long subwayId) {
        if (subwayId == null) {
            return ServiceResult.notFound();
        }
        Subway subway = subwayRepository.findOne(subwayId);
        if (subway == null) {
            return ServiceResult.notFound();
        }
        return ServiceResult.of(modelMapper.map(subway, SubwayDTO.class));

    }

    @Override
    public ServiceResult<SubwayStationDTO> findSubwayStation(Long subwayStationId) {
        SubwayStation station = subwayStationRepository.findOne(subwayStationId);
        SubwayStationDTO stationDTO = modelMapper.map(station,SubwayStationDTO.class);
        return ServiceResult.of(stationDTO);
    }

    @Override
    public ServiceResult<SupportAddressDTO> findCity(String cityEnName) {
        if(cityEnName == null){
            return ServiceResult.notFound();
        }
        SupportAddress supportAddress = supportAddressRepository.findByEnNameAndLevel(cityEnName, SupportAddress.Level.CITY.getValue());
        if (supportAddress == null) {
            return ServiceResult.notFound();
        }
        SupportAddressDTO addressDTO = modelMapper.map(supportAddress, SupportAddressDTO.class);
        return ServiceResult.of(addressDTO);
    }

    @Override
    public ServiceResult<BaiduMapLocation> getBaiduMapLocation(String city, String address) {
        String encodeAddress;
        String encodeCity;
        try{
            encodeAddress = URLEncoder.encode(address, "UTF-8");
            encodeCity = URLEncoder.encode(city, "UTF-8");
        }catch (UnsupportedEncodingException e) {
            logger.error("Error to encode house address", e);
            return new ServiceResult<BaiduMapLocation>(false, "Error to encode hosue address");
        }
        HttpClient httpClient = HttpClients.createDefault();
        StringBuilder sb = new StringBuilder(BAIDU_MAP_GEOCONV_API);
        //按照API 要求拼接参数
        sb.append("address=").append(encodeAddress).append("&")
                .append("city=").append(encodeCity).append("&")
                .append("output=json&")
                .append("ak=").append(BAIDU_MAP_KEY);

        HttpGet get = new HttpGet(sb.toString());
        try {
            HttpResponse response = httpClient.execute(get);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                return new ServiceResult<BaiduMapLocation>(false, "Can not get baidu map location");
            }
            String result = EntityUtils.toString(response.getEntity(),"UTF-8");
            JsonNode jsonNode = objectMapper.readTree(result);
            int status = jsonNode.get("status").asInt();
            if(status != 0){
                return new ServiceResult<BaiduMapLocation>(false, "Error to get map location for status: " + status);
            }
             BaiduMapLocation location = new BaiduMapLocation();
            JsonNode jsonLocation = jsonNode.get("result").get("location");
            location.setLongitude(jsonLocation.get("lng").asDouble());
            location.setLatitude(jsonLocation.get("lat").asDouble());
            return ServiceResult.of(location);
        } catch (IOException e) {
            logger.error("Error to fetch baidumap api", e);
            return new ServiceResult<BaiduMapLocation>(false, "Error to fetch baidumap api");
        }
    }
}
