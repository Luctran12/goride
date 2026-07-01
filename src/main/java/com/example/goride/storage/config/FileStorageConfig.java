package com.example.goride.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig implements WebMvcConfigurer {
    private final FileStorageProperties properties;

    public FileStorageConfig(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (properties.getProvider() != FileStorageProperties.Provider.LOCAL) {
            return;
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(localResourceLocation());
    }

    private String localResourceLocation() {
        String location = properties.getLocalRoot().toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}