package com.atopy.Atopass.domain.chatbot.controller;

import com.atopy.Atopass.domain.chatbot.service.SkinSignImages;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 증상 예시 사진. 챗봇이 사진을 보여 줘도 되는지 묻고, 사용자가 보여 달라고 답한 뒤에만 화면이 불러온다.
 * 로그인한 사용자만 받을 수 있고, 사진은 바뀌지 않으므로 브라우저에 하루 동안 둔다.
 */
@RestController
@RequestMapping("/chatbot/skin-signs")
@RequiredArgsConstructor
public class SkinSignImageController {

	private final SkinSignImages skinSignImages;

	@GetMapping("/{sign}/image")
	public ResponseEntity<byte[]> image(@PathVariable SkinSign sign) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
			.body(skinSignImages.load(sign));
	}

}
