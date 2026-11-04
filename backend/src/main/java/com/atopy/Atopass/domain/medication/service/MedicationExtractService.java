package com.atopy.Atopass.domain.medication.service;

import com.atopy.Atopass.domain.chatbot.entity.LlmUsageLog;
import com.atopy.Atopass.domain.chatbot.llm.ChatModelClient;
import com.atopy.Atopass.domain.chatbot.llm.LlmImage;
import com.atopy.Atopass.domain.chatbot.service.ChatConsentService;
import com.atopy.Atopass.domain.chatbot.service.LlmUsageService;
import com.atopy.Atopass.domain.medication.dto.response.MedicationExtractResponse;
import com.atopy.Atopass.domain.record.entity.PhotoType;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 약 봉투 사진을 AI(Claude)로 읽어 약 정보를 돌려준다. 저장하지 않는다.
 *
 * <ul>
 *   <li>사진 원본은 메모리에서만 다루고 디스크·DB·로그에 남기지 않는다.</li>
 *   <li>회원 이메일 등 식별 정보는 AI에 보내지 않는다. 사진과 안내 글만 보낸다.</li>
 *   <li>환자 이름, 주민등록번호, 병원·의사·약국 이름은 읽지 말라고 안내하고, 응답에서도 약속한 칸만 꺼내 쓴다.</li>
 *   <li>챗봇과 같은 동의가 필요하고, 하루 이용 한도는 챗봇 메시지와 합산한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MedicationExtractService {

	static final int MAX_FILES = 3;

	private static final String SYSTEM_PROMPT = """
		당신은 약 봉투(약국에서 받은 약 봉지)나 약 설명서 사진을 읽고 약 정보를 정리하는 도우미입니다.

		반드시 JSON 배열만 출력하세요. 설명, 인사, 코드 블록(```)을 붙이지 마세요.
		배열의 각 원소는 약 한 개이고 아래 칸만 가집니다.
		- name: 약 이름 (문자열)
		- type: "oral"(먹는 약), "topical"(바르는 약), "other"(그 밖) 중 하나. 확실하지 않으면 null
		- dose: 1회 용량 (예: "1정", "10mg"). 모르면 null
		- timesText: 사진에 적힌 복용 시간 원문 (예: "1일 2회 식후 30분"). 모르면 null
		- times: 복용 시간대가 분명할 때만 "morning", "lunch", "evening", "bedtime" 중에서 담은 배열.
		  "1일 2회"처럼 시간대가 분명하지 않으면 빈 배열 []
		- days: 처방 일수 (정수). 모르면 null

		지켜야 할 것:
		- 환자 이름, 주민등록번호, 병원 이름, 의사 이름, 약국 이름, 전화번호, 주소는 읽지도 출력하지도 마세요.
		- 사진에 적힌 것만 옮기고, 보이지 않거나 확실하지 않은 값은 추측하지 말고 null로 두세요.
		- 여러 장에 같은 약 이름이 나오면 하나로 합치세요.
		- 용량이 적절한지, 복용을 권하는 말, 약의 효과 설명은 쓰지 마세요.
		- 약을 찾을 수 없으면 빈 배열 []만 출력하세요.""";

	private static final String USER_TEXT = "사진에서 약 정보를 읽어 JSON 배열로만 답해 주세요.";

	private final ChatConsentService consentService;
	private final LlmUsageService usageService;
	private final ChatModelClient modelClient;
	private final MedicationExtractParser parser;

	public MedicationExtractResponse extract(Long memberId, List<MultipartFile> files) {
		// 사진이 외부 AI로 나가므로, 동의하지 않은 회원은 AI를 부르기 전에 막는다.
		consentService.requireConsent(memberId);
		List<LlmImage> images = readImages(files);
		usageService.requireWithinDailyLimit(memberId);

		String raw = modelClient.completeWithImages(SYSTEM_PROMPT, USER_TEXT, images);
		// AI 호출이 성공했을 때만 이용량에 센다. (장애로 못 읽은 것까지 한도를 쓰게 하지 않는다)
		usageService.record(memberId, LlmUsageLog.KIND_MEDICATION_EXTRACT);

		List<MedicationExtractResponse.Item> items = parser.parse(raw);
		if (items.isEmpty()) {
			throw new BusinessException(ErrorCode.MEDICATION_NOT_RECOGNIZED);
		}
		return new MedicationExtractResponse(items);
	}

	private List<LlmImage> readImages(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "약 봉투 사진을 올려 주세요.");
		}
		if (files.size() > MAX_FILES) {
			throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "사진은 " + MAX_FILES + "장까지 올릴 수 있습니다.");
		}
		return files.stream().map(MedicationExtractService::toImage).toList();
	}

	private static LlmImage toImage(MultipartFile file) {
		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("업로드된 파일을 읽지 못했습니다.", e);
		}
		PhotoType type = PhotoType.detect(content)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));
		return new LlmImage(content, type.contentType());
	}

}
