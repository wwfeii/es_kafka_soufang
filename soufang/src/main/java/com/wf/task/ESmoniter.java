package com.wf.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ESmoniter {
    private static final String HEALTH_CHECK_API = "http://127.0.0.1:9200/_cluster/health";

    private static final String GREEN = "green";
    private static final String YELLOW = "yellow";
    private static final String RED = "red";
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.mail.username}")
    private String fromMail;
    @Autowired
    private JavaMailSender mailSender;

    //@Scheduled(fixedDelay = 5000)
    public void checkHealth(){
        System.out.println("调用定时任务。。。。。。");
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet get = new HttpGet(HEALTH_CHECK_API);
        try{
            HttpResponse response = httpClient.execute(get);
            if(response.getStatusLine().getStatusCode()!=HttpServletResponse.SC_OK){
                System.out.println("Can not access ES Service normally! Please check the server.");
            }else{
               String body = EntityUtils.toString(response.getEntity(),"UTF-8");
                JsonNode node = objectMapper.readTree(body);
                String status = node.get("status").asText();

                String message = "";
                boolean isNormal = false;
                switch (status) {
                    case GREEN:
                        message = "ES server run normally.";
                        isNormal = true;
                        break;
                    case YELLOW:
                        message = "ES server gets status yellow! Please check the ES server!";
                        break;
                    case RED:
                        message = "ES ser get status RED!!! Must Check ES Server!!!";
                        break;
                    default:
                        message = "Unknown ES status from server for: " + status + ". Please check it.";
                        break;
                }

                if(!isNormal){
                    sendAlertMessage(message);
                }
                int totalNodes = node.get("number_of_data_nodes").asInt();
                if(totalNodes<5){
                    sendAlertMessage("集群节点丢失了哦，test,wf to 周香香");
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    /**
     * 发送邮件
     * @param s
     */
    private void sendAlertMessage(String s) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromMail);
        mailMessage.setTo("2833177813@qq.com");
        mailMessage.setSubject("【警告】ES服务监控");
        mailMessage.setText(s);

        mailSender.send(mailMessage);
    }
}
