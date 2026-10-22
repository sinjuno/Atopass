package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.record.entity.SkinSign;
import org.junit.jupiter.api.Test;

class SkinSignQuestionTest {

	@Test
	void 증상_이름과_묻는_말이_함께_있으면_그_증상을_묻는_것이다() {
		assertThat(SkinSignQuestion.detect("태선화가 뭐예요?")).contains(SkinSign.LICHENIFICATION);
		assertThat(SkinSignQuestion.detect("홍반은 어떻게 생겼어요")).contains(SkinSign.ERYTHEMA);
		assertThat(SkinSignQuestion.detect("구진 사진 보여 줘")).contains(SkinSign.EDEMA);
		assertThat(SkinSignQuestion.detect("긁은 자국이 무슨 뜻이야?")).contains(SkinSign.EXCORIATION);
	}

	@Test
	void 증상을_알려_주는_말이나_증상_이름이_없는_말은_묻는_것이_아니다() {
		assertThat(SkinSignQuestion.detect("팔에 진물이 나요")).isEmpty();
		assertThat(SkinSignQuestion.detect("오늘 뭐 기록해요?")).isEmpty();
		assertThat(SkinSignQuestion.detect("가려움이 뭐예요?")).isEmpty();
	}

	@Test
	void 증상이_여러_개면_먼저_나온_것이다() {
		assertThat(SkinSignQuestion.detect("건조증이랑 홍반은 뭐가 달라요?")).contains(SkinSign.XEROSIS);
	}

	@Test
	void 설명은_받침에_맞는_조사를_쓴다() {
		assertThat(SkinSignQuestion.explanation(SkinSign.ERYTHEMA)).isEqualTo("'홍반'은 피부가 붉어짐을 뜻해요.");
		assertThat(SkinSignQuestion.explanation(SkinSign.OOZING))
			.isEqualTo("'진물/딱지'는 상처에서 진물이 나오거나 딱지가 앉음을 뜻해요.");
	}

}
