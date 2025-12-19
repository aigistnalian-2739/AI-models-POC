package com.ltim.poc.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Exposes the 'generated' folder on your desktop/project to the web
        registry.addResourceHandler("/generated/**")
                .addResourceLocations("file:generated/");
    }
}