package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.service.ChatReplyParser.ModelOutput;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ChatReplyParserTest {

	private final ChatReplyParser parser = new ChatReplyParser(JsonMapper.builder().build());

	@Test
	void 정상_JSON을_읽는다() {
		Optional<ModelOutput> output = parser.parse("""
			{"understood": true, "reply": "수면은 몇 시간이었나요?",
			 "updates": {"sleepHours": 6}, "finished": false}""");

		assertThat(output).isPresent();
		assertThat(output.get().understood()).isTrue();
		assertThat(output.get().reply()).isEqualTo("수면은 몇 시간이었나요?");
		assertThat(output.get().updates().path("sleepHours").intValue()).isEqualTo(6);
		assertThat(output.get().finished()).isFalse();
	}

	@Test
	void 코드블록과_설명이_섞여_있어도_JSON을_찾아_읽는다() {
		Optional<ModelOutput> output = parser.parse("""
			네, 정리했어요.
			```json
			{"understood": true, "reply": "다음 질문", "updates": {}, "finished": true}
			```
			""");

		assertThat(output).isPresent();
		assertThat(output.get().reply()).isEqualTo("다음 질문");
		assertThat(output.get().finished()).isTrue();
	}

	@Test
	void understood가_false면_그대로_전달한다() {
		Optional<ModelOutput> output = parser.parse("{\"understood\": false, \"reply\": \"다시 말씀해 주세요.\"}");

		assertThat(output).isPresent();
		assertThat(output.get().understood()).isFalse();
		assertThat(output.get().updates()).isNull();
	}

	@Test
	void 생략된_항목은_기본값을_쓴다() {
		Optional<ModelOutput> output = parser.parse("{}");

		assertThat(output).isPresent();
		assertThat(output.get().understood()).isTrue();
		assertThat(output.get().reply()).isNull();
		assertThat(output.get().updates()).isNull();
		assertThat(output.get().finished()).isFalse();
	}

	@Test
	void 빈_reply는_없는_것으로_본다() {
		assertThat(parser.parse("{\"reply\": \"   \"}").get().reply()).isNull();
	}

	@Test
	void JSON이_아니거나_깨져_있으면_비어_있는_결과를_준다() {
		assertThat(parser.parse(null)).isEmpty();
		assertThat(parser.parse("")).isEmpty();
		assertThat(parser.parse("그냥 일반 문장입니다.")).isEmpty();
		assertThat(parser.parse("{\"reply\": ")).isEmpty();
		assertThat(parser.parse("{깨진 json}")).isEmpty();
	}

}
