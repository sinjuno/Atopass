package com.atopy.Atopass.global.config;

import com.atopy.Atopass.global.security.CurrentMemberArgumentResolver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	public static final String API_PREFIX = "/api/v1";

	/** 모든 @RestController 경로 앞에 /api/v1 을 붙인다. 컨트롤러에는 /auth, /records 처럼 적는다. */
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX, c -> c.isAnnotationPresent(RestController.class));
	}

	/**
	 * 프론트(React)를 빌드한 파일을 이 서버가 함께 내려준다. 배포 이미지를 만들 때 frontend/dist가 static에 복사된다.
	 * 파일이 없는 화면 주소(/calendar 등)는 index.html을 줘서 새로고침해도 화면이 뜨게 하고,
	 * /api 경로는 건드리지 않아 기존처럼 JSON 오류가 나간다. 개발·테스트처럼 index.html이 없으면 아무것도 하지 않는다.
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 파일 이름에 해시가 붙는 빌드 결과물은 오래 캐시해도 된다.
		registry.addResourceHandler("/assets/**")
			.addResourceLocations("classpath:/static/assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.setCacheControl(CacheControl.noCache())
			.resourceChain(true)
			.addResolver(new SpaFallbackResolver());
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentMemberArgumentResolver());
	}

	/** 요청한 파일이 있으면 그 파일을, 없으면(API 경로 제외) index.html을 돌려준다. */
	private static class SpaFallbackResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource requested = location.createRelative(resourcePath);
			if (requested.isReadable()) {
				return requested;
			}
			if (resourcePath.startsWith("api/")) {
				return null;
			}
			Resource index = new ClassPathResource("static/index.html");
			return index.isReadable() ? index : null;
		}

	}

}
