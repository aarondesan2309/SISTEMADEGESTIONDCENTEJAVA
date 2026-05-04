package com.emi.gestiondocente.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiTenantDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();
    private final String defaultUrl;
    private final String username;
    private final String password;

    public MultiTenantDataSource(DataSource defaultDataSource, String defaultUrl, String username, String password) {
        this.defaultUrl = defaultUrl;
        this.username = username;
        this.password = password;
        
        setDefaultTargetDataSource(defaultDataSource);
        targetDataSources.put("gestion_docente_emi", defaultDataSource); // Fallback inicial
        setTargetDataSources(targetDataSources);
    }

    @Override
    protected String determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = (String) determineCurrentLookupKey();
        if (tenantId != null && !targetDataSources.containsKey(tenantId)) {
            addTenant(tenantId);
        }
        return super.determineTargetDataSource();
    }

    public void addTenant(String tenantId) {
        if (!targetDataSources.containsKey(tenantId)) {
            // Reemplazar la base de datos en la URL
            // asumiendo formato jdbc:postgresql://host:port/dbname
            String newUrl = defaultUrl.substring(0, defaultUrl.lastIndexOf('/') + 1) + tenantId;
            
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(newUrl);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");
            
            targetDataSources.put(tenantId, ds);
            afterPropertiesSet(); // Refrescar el AbstractRoutingDataSource
        }
    }
}
