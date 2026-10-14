package com.atopy.Skinlog.global.config;

import com.atopy.Skinlog.global.auth.CurrentMemberArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// TEMP STUB — 백1 공통 모듈(JWT 인증) 머지 시 교체
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    public WebConfig(CurrentMemberArgumentResolver currentMemberArgumentResolver) {
        this.currentMemberArgumentResolver = currentMemberArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}
