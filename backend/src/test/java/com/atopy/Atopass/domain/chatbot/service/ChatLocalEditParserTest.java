package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ChatLocalEditParserTest {

	private final ChatLocalEditParser parser = new ChatLocalEditParser(JsonMapper.builder().build());

	@Test
	void 수면_시간을_고치는_말을_알아듣는다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("수면 시간을 6시간으로 고쳐줘");

		assertThat(result).isPresent();
		assertThat(result.get().understood()).isTrue();
		assertThat(result.get().updates().path("sleepHours").doubleValue()).isEqualTo(6.0);
	}

	@Test
	void 잠_몇시간_잤다는_말투도_알아듣는다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("어제 7시간 잤어");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("sleepHours").doubleValue()).isEqualTo(7.0);
	}

	@Test
	void 고통_지수를_고치는_말을_알아듣는다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("불편함 지수는 7점이야");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("painIndex").intValue()).isEqualTo(7);
	}

	@Test
	void 약_복용_여부를_알아듣는다() {
		assertThat(parser.parse("약 먹었어").get().updates().path("medication").booleanValue()).isTrue();
		assertThat(parser.parse("오늘은 약 안 먹었어").get().updates().path("medication").booleanValue()).isFalse();
	}

	@Test
	void 여러_항목이_섞인_문장도_한번에_반영한다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("고통은 3점이고 잠은 6시간 잤어");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("painIndex").intValue()).isEqualTo(3);
		assertThat(result.get().updates().path("sleepHours").doubleValue()).isEqualTo(6.0);
	}

	@Test
	void 증상이_없어졌다는_말은_removeSkinSigns로_보낸다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("가려움은 없어졌어요");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("removeSkinSigns").get(0).asString()).isEqualTo("ITCHING");
	}

	@Test
	void 증상이_생겼다는_말은_skinSigns로_보낸다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("홍반이 생겼어요");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("skinSigns").get(0).asString()).isEqualTo("ERYTHEMA");
	}

	@Test
	void 부위를_빼달라는_말을_알아듣는다() {
		Optional<ChatReplyParser.ModelOutput> result = parser.parse("얼굴은 빼 주세요");

		assertThat(result).isPresent();
		assertThat(result.get().updates().path("removeBodyParts").get(0).asString()).isEqualTo("FACE");
	}

	@Test
	void 범위를_벗어난_점수는_알아듣지_못한_것으로_본다() {
		assertThat(parser.parse("고통은 15점이야")).isEmpty();
	}

	@Test
	void 알아들을_항목이_없으면_비어_있다() {
		assertThat(parser.parse("오늘 날씨가 참 좋네요")).isEmpty();
		assertThat(parser.parse(null)).isEmpty();
		assertThat(parser.parse("  ")).isEmpty();
	}

}
