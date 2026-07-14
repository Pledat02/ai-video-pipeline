package com.aivideo.pipeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serve thư mục pipeline-output (audio/video/subtitle đã sinh) qua HTTP tại /media/**
 * để xem/nghe thử trực tiếp từ dashboard, không cần mở file thủ công.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final String workDir;

    public StaticResourceConfig(@Value("${pipeline.work-dir}") String workDir) {
        this.workDir = workDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(workDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}
