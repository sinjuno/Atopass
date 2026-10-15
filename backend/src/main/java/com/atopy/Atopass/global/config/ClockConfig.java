package com.atopy.Atopass.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ClockConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	/** "오늘" 날짜 판단에 쓰는 시계. 서비스 기준 시간대(한국)로 고정한다. */
	@Bean
	public Clock clock() {
		return Clock.system(ZONE);
	}

}
