package com.wf.service.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Longs;
import com.wf.base.HouseSort;
import com.wf.base.RentValueBlock;
import com.wf.entity.House;
import com.wf.entity.HouseDetail;
import com.wf.entity.HouseTag;
import com.wf.repository.HouseDetailRepository;
import com.wf.repository.HouseRepository;
import com.wf.repository.HouseTagRepository;
import com.wf.service.ServiceMultiResult;
import com.wf.web.form.RentSearch;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService  implements  ISearchService{
    private static final Logger logger = LoggerFactory.getLogger(ISearchService.class);
    private static final String INDEX_NAME = "xunwu";
    private static final String INDEX_TOPIC = "house_build";
    private static final String INDEX_TYPE = "house";

    @Autowired
    private TransportClient esClient;
    @Autowired
    private HouseRepository houseRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HouseTagRepository tagRepository;
    @Autowired
    private HouseDetailRepository detailRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = INDEX_TOPIC )
    private void handleMessage(String content){
        try{
            HouseIndexMessage message = objectMapper.readValue(content, HouseIndexMessage.class);
            switch (message.getOperation()){
                case HouseIndexMessage.INDEX:
                    this.createOrUpdateIndex(message);
                    break;
                case HouseIndexMessage.REMOVE:
                    this.removeIndex(message);
                    break;
                default:
                    logger.warn("Not support message content " + content);
                    break;
            }
        }catch (IOException e){
            logger.error("cannot parse json for"+content,e);
        }
    }

    private void removeIndex(HouseIndexMessage message) {
        Long houseId = message.getHouseId();
        DeleteByQueryRequestBuilder builder = DeleteByQueryAction.INSTANCE
                .newRequestBuilder(esClient)
                .filter(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID,houseId))
                .source(INDEX_NAME);
        logger.debug("Delete by query for house: " + builder);
        BulkByScrollResponse response = builder.get();
        long deleted = response.getDeleted();
        logger.debug("Delete total:"+deleted);

        if(deleted<0){
            this.remove(houseId,message.getRetry()+1);
        }
    }

    private void createOrUpdateIndex(HouseIndexMessage message) {
        Long houseId = message.getHouseId();
        House house = houseRepository.findOne(houseId);
        if(house == null){
            logger.error("Index house {} dose not exist!",houseId);
            return ;
        }
        HouseIndexTemplate indexTemplate = new HouseIndexTemplate();
        modelMapper.map(house,indexTemplate);

        HouseDetail detail = detailRepository.findByHouseId(houseId);
        if(detail == null){
            return ;
        }
        modelMapper.map(detail,indexTemplate);

        List<HouseTag> tags = tagRepository.findAllByHouseId(houseId);
        if(tags != null && !tags.isEmpty()){
            List<String> tagStrings = new ArrayList<>();
            tags.forEach(tag ->
                    tagStrings.add(tag.getName()));
            indexTemplate.setTags(tagStrings);
        }
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID,houseId));
        logger.debug(requestBuilder.toString());
        SearchResponse response = requestBuilder.get();
        long totalHit = response.getHits().getTotalHits();
        if(totalHit == 0){
            create(indexTemplate);
        }else if(totalHit == 1){
            String esId = response.getHits().getAt(0).getId();
            update(esId,indexTemplate);
        }else{
            deleteAndCreate(totalHit,indexTemplate);
        }
    }

    @Override
    public void index(Long houseId) {
        this.index(houseId,0);
    }
    private void index(Long houseId,int retry){
        if(retry>HouseIndexMessage.MAX_RETRY){
            logger.error("Retry index times over 3 for house: " + houseId + " Please check it!");
            return;
        }
        HouseIndexMessage message = new HouseIndexMessage(houseId,HouseIndexMessage.INDEX,retry);
        try {
            kafkaTemplate.send(INDEX_TOPIC,objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            logger.error("Json encode error for " + message);
        }
    }

    @Override
    public void remove(Long houseId) {
        this.remove(houseId,0);
    }

    @Override
    public ServiceMultiResult<Long> query(RentSearch rentSearch) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(
                QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,rentSearch.getCityEnName())
        );
        if(rentSearch.getRegionEnName() != null && !"*".equals(rentSearch.getRegionEnName())){
            boolQuery.filter(
                    QueryBuilders.termQuery(HouseIndexKey.REGION_EN_NAME,rentSearch.getRegionEnName())
            );
        }
        RentValueBlock area = RentValueBlock.matchArea(rentSearch.getAreaBlock());
        if(!RentValueBlock.ALL.equals(area)){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(HouseIndexKey.AREA);
            if(area.getMax()>0){
                rangeQuery.lte(area.getMax());
            }
            if(area.getMin()>0){
                rangeQuery.gte(area.getMin());
            }
            boolQuery.filter(rangeQuery);
        }
        RentValueBlock price = RentValueBlock.matchPrice(rentSearch.getPriceBlock());
        if(!RentValueBlock.ALL.equals(price)){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(HouseIndexKey.PRICE);
            if(price.getMax()>0){
                rangeQuery.lte(price.getMax());
            }
            if(price.getMin()>0){
                rangeQuery.gte(price.getMin());
            }
            boolQuery.filter(rangeQuery);
        }
        if(rentSearch.getRentWay()>-1){
            boolQuery.filter(
                    QueryBuilders.termQuery(HouseIndexKey.RENT_WAY,rentSearch.getRentWay())
            );
        }
        boolQuery.must(
                QueryBuilders.multiMatchQuery(rentSearch.getKeywords(),
                        HouseIndexKey.TITLE,
                        HouseIndexKey.TRAFFIC,
                        HouseIndexKey.DISTRICT,
                        HouseIndexKey.ROUND_SERVICE,
                        HouseIndexKey.SUBWAY_LINE_NAME,
                        HouseIndexKey.SUBWAY_STATION_NAME
                )
        );
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQuery)
                .addSort(
                        HouseSort.getSortKey(rentSearch.getOrderBy()),
                        SortOrder.fromString(rentSearch.getOrderDirection())
                )
                .setFrom(rentSearch.getStart())
                .setSize(rentSearch.getSize())
                .setFetchSource(HouseIndexKey.HOUSE_ID, null);
        logger.info(requestBuilder.toString());

        List<Long> houseIds = new ArrayList<>();
        SearchResponse response = requestBuilder.get();
        if(response.status() != RestStatus.OK){
            logger.warn("Search status is no ok for " + requestBuilder);
            return new ServiceMultiResult<>(0, houseIds);
        }
        for(SearchHit hit: response.getHits()){
            System.out.println(hit.getSource());
            houseIds.add(Longs.tryParse(String.valueOf(hit.getSource().get(HouseIndexKey.HOUSE_ID))));
        }
        return new ServiceMultiResult<>(response.getHits().totalHits,houseIds);
    }

    private void remove(Long houseId,int retry){
        if(retry>HouseIndexMessage.MAX_RETRY){
            logger.error("Retry remove times over 3 for house: " + houseId + " Please check it!");
            return;
        }
        HouseIndexMessage message = new HouseIndexMessage(houseId,HouseIndexMessage.REMOVE,retry);
        try {
            this.kafkaTemplate.send(INDEX_TOPIC,objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            logger.error("Cannot encode json for " + message, e);
        }
    }

    /**
     * 创建索引
     * @param indexTemplate
     * @return
     */
    private boolean create(HouseIndexTemplate indexTemplate){
        try{
            IndexResponse response = this.esClient.prepareIndex(INDEX_NAME,INDEX_TYPE)
                    .setSource(objectMapper.writeValueAsBytes(indexTemplate),XContentType.JSON).get();
            logger.debug("create index with house:"+indexTemplate.getHouseId());
            if(response.status() == RestStatus.CREATED){
                return true;
            }else{
                return false;
            }
        }catch (JsonProcessingException e){
            logger.error("ERROR to index house"+indexTemplate.getHouseId(),e);
            return false;
        }
    }

    /**
     * 更新索引
     * @param esId
     * @param indexTemplate
     * @return
     */
    private boolean update(String esId,HouseIndexTemplate indexTemplate){
        try{
            UpdateResponse response = this.esClient.prepareUpdate(INDEX_NAME,INDEX_TYPE,esId)
                    .setDoc(objectMapper.writeValueAsBytes(indexTemplate),XContentType.JSON).get();
            logger.debug("Update index with house: " + indexTemplate.getHouseId());
            if(response.status() == RestStatus.OK){
                return true;
            }else{
               return  false;
            }
        }catch (JsonProcessingException e){
            logger.error("Error to index house " + indexTemplate.getHouseId(), e);
            return false;

        }
    }

    /**
     * 删除 并且创建
     * @param totalHit
     * @param indexTemplate
     * @return
     */
    private boolean deleteAndCreate(long totalHit,HouseIndexTemplate indexTemplate){
        DeleteByQueryRequestBuilder builder = DeleteByQueryAction.INSTANCE
                .newRequestBuilder(esClient)
                .filter(QueryBuilders.termQuery(HouseIndexKey.HOUSE_ID,indexTemplate.getHouseId()))
                .source(INDEX_NAME);
        logger.debug("Delete by query for house: " + builder);
        BulkByScrollResponse response = builder.get();
        long deleted = response.getDeleted();
        if(deleted != totalHit){
            logger.warn("Need delete {}, but {} was deleted!", totalHit, deleted);
            return false;
        } else {
            return create(indexTemplate);
        }
    }
}
