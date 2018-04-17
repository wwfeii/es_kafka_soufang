//package com.wf.service.search;
//
//import com.wf.ApplicationTests;
//import org.junit.Assert;
//import org.junit.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//
//public class SearchServiceTest extends ApplicationTests {
//    @Autowired
//    private ISearchService searchService;
//
//    @Test
//    public void testIndex(){
//        Long targetHouseId = 15L;
//        boolean success = searchService.index(15L);
//        Assert.assertTrue(success);
//    }
//
//    @Test
//    public void testRemove(){
//        Long targetHouseId = 15L;
//        Boolean success = searchService.remove(15L);
//        Assert.assertTrue(success);
//    }
//}
