package com.aiassistant.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.aiassistant.learning.mapper")
@EnableAsync
@SpringBootApplication
public class AiLearningAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiLearningAssistantApplication.class, args);
    }
}
