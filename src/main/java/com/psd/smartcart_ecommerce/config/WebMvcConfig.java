package com.psd.smartcart_ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Value("${project.image}")
    private String imagePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve local uploaded images first, then fallback to classpath static images.
        File imageDir = new File(System.getProperty("user.dir"), imagePath);
        String absoluteImagePath = "file:" + imageDir.getAbsolutePath() + File.separator;

        registry.addResourceHandler("/images/**")
                .addResourceLocations(absoluteImagePath, "classpath:/static/images/")
                .setCachePeriod(0) // Disable caching for development
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        Resource fallbackResource = new ClassPathResource("static/images/image-placeholder.svg");
                        return fallbackResource.exists() ? fallbackResource : null;
                    }
                });
    }
}
