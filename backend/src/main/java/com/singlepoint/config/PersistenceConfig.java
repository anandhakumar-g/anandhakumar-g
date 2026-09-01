package com.singlepoint.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Replaces Boot's auto-configured {@link DataSource} with one that applies the
 * {@code app.current_tenant_id} GUC per connection (see {@link TenantAwareDataSource}).
 */
@Configuration
public class PersistenceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource hikari = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        if (hikari.getPoolName() == null || hikari.getPoolName().startsWith("HikariPool-")) {
            hikari.setPoolName("single-point-pool");
        }
        return new TenantAwareDataSource(hikari);
    }
}
