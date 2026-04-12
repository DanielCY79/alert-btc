package com.mobai.alert.access.kline.sync;

import com.mobai.alert.access.kline.persistence.repository.AccessKlineBarJdbcRepository;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.config.RestTemplateConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(scanBasePackageClasses = {
        BinanceKlineSyncApplication.class,
        KlineSyncPersistenceConfig.class,
        RestTemplateConfig.class,
        BinanceKlineRestClient.class,
        AccessKlineBarJdbcRepository.class
})
@MapperScan("com.mobai.alert.access.kline.persistence.mapper")
public class BinanceKlineSyncApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(BinanceKlineSyncApplication.class)
                .properties(
                        "spring.main.web-application-type=none"
                )
                .run(args);
    }
}
