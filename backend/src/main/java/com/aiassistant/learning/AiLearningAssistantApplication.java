package com.aiassistant.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 项目的 Spring Boot 启动入口。
 *
 * <p>初学者可以把这个类理解为“后端程序的大门”：
 * {@link SpringBootApplication} 会启动 Spring 容器，
 * {@link MapperScan} 会让 MyBatis-Plus 找到 mapper 包中的数据库访问接口，
 * {@link EnableAsync} 会开启异步任务能力。</p>
 */
@MapperScan("com.aiassistant.learning.mapper")
@EnableAsync
@SpringBootApplication
public class AiLearningAssistantApplication {

    /**
     * Java 程序入口方法，运行后会启动整个后端服务。
     *
     * @param args 命令行参数，普通本地启动时通常不需要手动传入
     */
    public static void main(String[] args) {
        SpringApplication.run(AiLearningAssistantApplication.class, args);
    }
}
