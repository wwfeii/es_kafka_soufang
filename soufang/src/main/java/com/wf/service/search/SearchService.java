package com.wf.service.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.wf.base.HouseSort;
import com.wf.base.RentValueBlock;
import com.wf.entity.House;
import com.wf.entity.HouseDetail;
import com.wf.entity.HouseTag;
import com.wf.entity.SupportAddress;
import com.wf.repository.HouseDetailRepository;
import com.wf.repository.HouseRepository;
import com.wf.repository.HouseTagRepository;
import com.wf.repository.SupportAddressRepository;
import com.wf.service.ServiceMultiResult;
import com.wf.service.ServiceResult;
import com.wf.service.house.IAddressService;
import com.wf.web.form.RentSearch;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeAction;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
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
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private IAddressService addressService;

    @Autowired
    private HouseTagRepository tagRepository;
    @Autowired
    private HouseDetailRepository detailRepository;
    @Autowired
    private SupportAddressRepository supportAddressRepository;

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

        //在添加索引的时候，通过具体地址去百度地图中查询具体的金纬度，设置到索引中
        SupportAddress city = supportAddressRepository.findByEnNameAndLevel(house.getCityEnName(),SupportAddress.Level.CITY.getValue());
        SupportAddress region = supportAddressRepository.findByEnNameAndLevel(house.getRegionEnName(), SupportAddress.Level.REGION.getValue());
        String address = city.getCnName() + region.getCnName() + house.getStreet() + house.getDistrict() + detail.getDetailAddress();
        ServiceResult<BaiduMapLocation> location = addressService.getBaiduMapLocation(city.getCnName(), address);
        if (!location.isSuccess()) {
            this.index(message.getHouseId(), message.getRetry() + 1);
            return;
        }
        //设置到索引中
        indexTemplate.setLocation(location.getResult());
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
                .setFetchSource(HouseIndexKey.HOUSE_ID, null);//提高查询效率，只需要查询houseID
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

    @Override
    public ServiceResult<List<String>> suggest(String prefix) {
        CompletionSuggestionBuilder suggesttion = SuggestBuilders.completionSuggestion("suggest").prefix(prefix).size(5);//设置查询
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        suggestBuilder.addSuggestion("autocomplete",suggesttion);

        SearchRequestBuilder suggest = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .suggest(suggestBuilder);
        logger.debug(suggest.toString());
        SearchResponse response = suggest.get();
        Suggest resSuggest = response.getSuggest();
        if(resSuggest == null){
            return ServiceResult.of(new ArrayList<>());
        }
        Suggest.Suggestion result = resSuggest.getSuggestion("autocomplete");
        int maxSuggest = 0;
        Set<String> suggestSet = new HashSet<>();
        for(Object term : result.getEntries()){
            if(term instanceof CompletionSuggestion.Entry){
                CompletionSuggestion.Entry item = (CompletionSuggestion.Entry)term;
                if(item.getOptions().isEmpty()){
                    continue;
                }
                for(CompletionSuggestion.Entry.Option option : item.getOptions()){
                    String tip = option.getText().string();
                    if(suggestSet.contains(tip)){
                        continue;
                    }
                    suggestSet.add(tip);
                    maxSuggest++;
                }
            }
            if(maxSuggest>5){
                break;
            }
        }
       List<String> suggests =  Lists.newArrayList(suggestSet.toArray(new String[]{}));
        return ServiceResult.of(suggests);
    }

    @Override
    public ServiceResult<Long> aggregateDistrictHouse(String enName, String regionName, String district) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME, enName))
                .filter(QueryBuilders.termQuery(HouseIndexKey.REGION_EN_NAME, regionName))
                .filter(QueryBuilders.termQuery(HouseIndexKey.DISTRICT, district));
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQueryBuilder)
                .addAggregation(
                        AggregationBuilders.terms(HouseIndexKey.AGG_DISTRICT)
                                .field(HouseIndexKey.DISTRICT)
                ).setSize(0);
        logger.debug(requestBuilder.toString());

        SearchResponse response = requestBuilder.get();
        if(response.status() == RestStatus.OK){
            Terms terms  = response.getAggregations().get(HouseIndexKey.AGG_DISTRICT);
            if(terms.getBuckets() != null && !terms.getBuckets().isEmpty()){
                return ServiceResult.of(terms.getBucketByKey(district).getDocCount());
            }
        }else {
            logger.warn("Failed to Aggregate for " + HouseIndexKey.AGG_DISTRICT);

        }

        return ServiceResult.of(0L);
    }

    @Override
    public ServiceMultiResult<HouseBucketDTO> mapAggregate(String cityEnName) {

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME,cityEnName));
        //聚合
        TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(HouseIndexKey.AGG_REGION).field(HouseIndexKey.REGION_EN_NAME);
        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQuery)
                .addAggregation(aggregationBuilder);
        logger.debug(requestBuilder.toString());
        SearchResponse response = requestBuilder.get();
        List<HouseBucketDTO> buckets = new ArrayList<>();
        if (response.status() != RestStatus.OK) {
            logger.warn("Aggregate status is not ok for " + requestBuilder);
            return new ServiceMultiResult<>(0, buckets);
        }

        Terms terms = response.getAggregations().get(HouseIndexKey.AGG_REGION);
        for(Terms.Bucket bucket : terms.getBuckets()){
            buckets.add(new HouseBucketDTO(bucket.getKeyAsString(),bucket.getDocCount()));
        }
        return new ServiceMultiResult<>(response.getHits().getTotalHits(),buckets);
    }

    @Override
    public ServiceMultiResult<Long> mapQuery(String cityEnName, String orderBy, String orderDirection, int start, int size) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        BoolQueryBuilder filter = boolQuery.filter(QueryBuilders.termQuery(HouseIndexKey.CITY_EN_NAME, cityEnName));

        SearchRequestBuilder requestBuilder = this.esClient.prepareSearch(INDEX_NAME)
                .setTypes(INDEX_TYPE)
                .setQuery(boolQuery)
                .addSort(HouseSort.getSortKey(orderBy), SortOrder.fromString(orderDirection))
                .setFrom(start)
                .setSize(size);
        List<Long> houseIds = new ArrayList<>();
        SearchResponse response = requestBuilder.get();
        if (response.status() != RestStatus.OK) {
            logger.warn("Search status is not ok for " + requestBuilder);
            return new ServiceMultiResult<>(0, houseIds);
        }
        for(SearchHit hit : response.getHits()){
            houseIds.add(Longs.tryParse(String.valueOf(hit.getSource().get(HouseIndexKey.HOUSE_ID))));
        }
        return new ServiceMultiResult<>(response.getHits().getTotalHits(), houseIds);
    }

    /**
     * 更新自动提示内容
     */
    private boolean updateSuggest(HouseIndexTemplate indexTemplate){
        AnalyzeRequestBuilder requestBuilder = new AnalyzeRequestBuilder(
                this.esClient, AnalyzeAction.INSTANCE, INDEX_NAME, indexTemplate.getTitle(),
                indexTemplate.getLayoutDesc(), indexTemplate.getRoundService(),
                indexTemplate.getDescription(), indexTemplate.getSubwayLineName(),
                indexTemplate.getSubwayStationName());
        requestBuilder.setAnalyzer("ik_smart");
        AnalyzeResponse response = requestBuilder.get();
        List<AnalyzeResponse.AnalyzeToken> tokens = response.getTokens();
        if(tokens == null){
            logger.warn("Can not analyze token for house: " + indexTemplate.getHouseId());
            return false;
        }
        List<HouseSuggest> suggests = new ArrayList<>();
        for(AnalyzeResponse.AnalyzeToken token : tokens){
            //排除数字类型  & 小于2个字符的分词结果
            if("<NUM>".equals(token.getType()) || token.getTerm().length()<2){
                continue;
            }
            HouseSuggest suggest = new HouseSuggest();
            suggest.setInput(token.getTerm());
            suggests.add(suggest);
        }
        //定制化 小区 自动补全
        HouseSuggest suggest = new HouseSuggest();
        suggest.setInput(indexTemplate.getDistrict());
        suggests.add(suggest);

        indexTemplate.setSuggest(suggests);
        return true;
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
        if(indexTemplate != null){
            updateSuggest(indexTemplate);
        }
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
        if(indexTemplate != null){
            updateSuggest(indexTemplate);
        }
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
