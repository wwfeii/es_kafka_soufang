package com.wf.service.house;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.wf.ApplicationTests;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

public class QNiuServiceTest extends ApplicationTests{

    @Autowired
    private IQiNiuService qiNiuService;

    @Test
    public void testUploadFile(){
        String fileName = "C:\\Users\\Nancy\\Desktop\\timg.jpg";
        File file = new File(fileName);
        Assert.assertTrue(file.exists());
        try{
            Response response = qiNiuService.uploadFile(file);
            Assert.assertTrue(response.isOK());
        }catch (QiniuException e){
            e.printStackTrace();
        }
    }

}
