package com.emi.gestiondocente.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String defaultUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setDriverClassName("org.postgresql.Driver");
        defaultDataSource.setUrl(defaultUrl);
        defaultDataSource.setUsername(username);
        defaultDataSource.setPassword(password);

        return new MultiTenantDataSource(defaultDataSource, defaultUrl, username, password);
    }
}
