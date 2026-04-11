package com.mobai.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用入口。
 * 负责启动监控调度、回测执行器以及其他基础组件。
 */
@SpringBootApplication
@EnableScheduling
public class AlertApplication {

	/**
	 * 启动应用上下文。
	 */
	public static void main(String[] args) {
		SpringApplication.run(AlertApplication.class, args);
	}

}
