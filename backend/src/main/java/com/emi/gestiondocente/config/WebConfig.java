package com.emi.gestiondocente.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${sgdc.storage.fotos}")
    private String fotosDir;

    @Value("${sgdc.storage.cedulas}")
    private String cedulasDir;

    @Value("${sgdc.storage.expedientes}")
    private String expedientesDir;

    private String toFileUrl(String path) {
        String normalized = path.replace("\\", "/");
        if (!normalized.endsWith("/")) normalized += "/";
        if (!normalized.startsWith("file:///")) {
            normalized = "file:///" + normalized.replaceFirst("^/+", "");
        }
        return normalized;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fotos/**").addResourceLocations(toFileUrl(fotosDir));
        registry.addResourceHandler("/cedulas/**").addResourceLocations(toFileUrl(cedulasDir));
        registry.addResourceHandler("/expedientes/**").addResourceLocations(toFileUrl(expedientesDir));
        registry.addResourceHandler("/temp_docx/**").addResourceLocations(toFileUrl(expedientesDir.replace("expedientes", "temp_docx")));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
