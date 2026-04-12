package com.mobai.alert.access.kline.sync;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class KlineSchemaInitializer {

    private final DataSource dataSource;

    public KlineSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("sql/access-kline-schema.sql")
        );
        populator.execute(dataSource);
    }
}
