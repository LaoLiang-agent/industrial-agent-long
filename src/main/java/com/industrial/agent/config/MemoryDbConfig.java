package com.industrial.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two datasources: the primary H2 work-order store (kept as before) and a
 * dedicated PostgreSQL store for the memory layer (L3 Summary, L4 Profile).
 *
 * Declaring an explicit DataSource bean disables Spring Boot's single-datasource
 * auto-config, so the H2 primary is re-declared here to preserve schema.sql init
 * and the default JdbcTemplate used by WorkOrderTool.
 */
@Configuration
public class MemoryDbConfig {

    @Value("${agent-memory.datasource.url}")
    private String memoryUrl;

    @Value("${agent-memory.datasource.username}")
    private String memoryUsername;

    @Value("${agent-memory.datasource.password}")
    private String memoryPassword;

    @Value("${agent-memory.datasource.driver-class-name}")
    private String memoryDriver;

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSource memoryDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(memoryUrl);
        ds.setUsername(memoryUsername);
        ds.setPassword(memoryPassword);
        ds.setDriverClassName(memoryDriver);
        ds.setPoolName("memory-pool");
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate memoryJdbcTemplate(@Qualifier("memoryDataSource") DataSource memoryDataSource) {
        return new JdbcTemplate(memoryDataSource);
    }
}
