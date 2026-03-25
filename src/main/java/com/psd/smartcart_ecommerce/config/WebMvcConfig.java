package com.psd.smartcart_ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Value("${project.image}")
    private String imagePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve images from the absolute path with proper Windows path handling
        File imageDir = new File(System.getProperty("user.dir"), imagePath);
        String absoluteImagePath = "file:" + imageDir.getAbsolutePath() + File.separator;
        
        System.out.println("Configuring image path: " + absoluteImagePath);
        
        registry.addResourceHandler("/images/**")
                .addResourceLocations(absoluteImagePath)
                .setCachePeriod(0); // Disable caching for development
        
        // Also add classpath images as fallback
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }
}
