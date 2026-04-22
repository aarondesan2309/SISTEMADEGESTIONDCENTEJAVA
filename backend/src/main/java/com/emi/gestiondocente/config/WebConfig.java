package com.emi.gestiondocente.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String BASE_UPLOAD_PATH = "file:///C:/temp/gestion-docente-web/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Exponer carpetas locales donde se guardan archivos
        registry.addResourceHandler("/fotos/**").addResourceLocations(BASE_UPLOAD_PATH + "fotos/");
        registry.addResourceHandler("/cedulas/**").addResourceLocations(BASE_UPLOAD_PATH + "cedulas/");
        registry.addResourceHandler("/expedientes/**").addResourceLocations(BASE_UPLOAD_PATH + "expedientes/");
        registry.addResourceHandler("/temp_docx/**").addResourceLocations(BASE_UPLOAD_PATH + "temp_docx/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirigir la raiz / a index.html para evitar el 404
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
