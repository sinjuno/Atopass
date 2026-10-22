package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 증상마다 하나씩 둔 예시 사진. 서버의 resources/skin-sign-images/{증상 이름}.png 에 있다.
 * 사진이 없는 증상(가려움)은 사진을 보여 줄지 묻지 않는다.
 * 챗봇은 사용자가 증상을 물었을 때 먼저 보여 줘도 되는지 묻고, 동의하면 그때 화면이 이 사진을 불러온다.
 */
@Component
public class SkinSignImages {

	private static final String DIRECTORY = "skin-sign-images/";

	/** 이 증상의 예시 사진이 있는지 */
	public boolean has(SkinSign sign) {
		return resource(sign).exists();
	}

	/** 예시 사진(PNG). 사진이 없는 증상이면 404 NOT_FOUND */
	public byte[] load(SkinSign sign) {
		ClassPathResource resource = resource(sign);
		if (!resource.exists()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "이 증상의 예시 사진이 없습니다.");
		}
		try (InputStream in = resource.getInputStream()) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "이 증상의 예시 사진이 없습니다.");
		}
	}

	private static ClassPathResource resource(SkinSign sign) {
		return new ClassPathResource(DIRECTORY + sign.name() + ".png");
	}

}
