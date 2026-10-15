package com.atopy.Skinlog.domain.report.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 파일 저장 위치. 웹으로 공개되는 경로(static 등) 밖을 지정해야 한다. */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
	@DefaultValue("./data/reports") String reportDir,
	@DefaultValue("./data/photos") String photoDir
) {
}
