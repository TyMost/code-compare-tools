package com.example.codecompare.rebuild;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 重构后端的启动入口，只加载重构版本的模块。
 */
@SpringBootApplication(scanBasePackages = "com.example.codecompare.rebuild")
public class RebuildCodeCompareApplication {

    public static void main(String[] args) {
        SpringApplication.run(RebuildCodeCompareApplication.class, args);
    }
}
