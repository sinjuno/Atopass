package com.atopy.Atopass.domain.chatbot.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Anthropic 공식 Java SDK로 Claude를 호출한다.
 * 사용자의 대화 내용은 로그에 남기지 않는다. 호출 실패 시 CHATBOT_UNAVAILABLE(503)로 알린다.
 */
@Slf4j
@Component
public class AnthropicChatModelClient implements ChatModelClient {

	private final LlmProperties properties;
	private volatile AnthropicClient client;

	public AnthropicChatModelClient(LlmProperties properties) {
		this.properties = properties;
	}

	@Override
	public String complete(String systemPrompt, List<ChatTurn> messages) {
		requireApiKey();
		MessageCreateParams.Builder builder = baseParams(systemPrompt);
		for (ChatTurn turn : messages) {
			if (turn.role() == ChatRole.USER) {
				builder.addUserMessage(turn.content());
			} else {
				builder.addAssistantMessage(turn.content());
			}
		}
		return send(builder);
	}

	@Override
	public String completeWithImages(String systemPrompt, String userText, List<LlmImage> images) {
		requireApiKey();
		List<ContentBlockParam> blocks = new ArrayList<>();
		for (LlmImage image : images) {
			Base64ImageSource source = Base64ImageSource.builder()
				.data(Base64.getEncoder().encodeToString(image.bytes()))
				.mediaType(mediaType(image.mediaType()))
				.build();
			blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build()));
		}
		blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(userText).build()));
		return send(baseParams(systemPrompt).addUserMessageOfBlockParams(blocks));
	}

	private void requireApiKey() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			log.warn("ANTHROPIC_API_KEY가 설정되지 않아 챗봇을 사용할 수 없습니다.");
			throw new BusinessException(ErrorCode.CHATBOT_UNAVAILABLE);
		}
	}

	private MessageCreateParams.Builder baseParams(String systemPrompt) {
		MessageCreateParams.Builder builder = MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(properties.maxTokens())
			.system(systemPrompt);
		OutputConfig.Effort effort = effort(properties.effort());
		if (effort != null) {
			builder.outputConfig(OutputConfig.builder().effort(effort).build());
		}
		return builder;
	}

	private String send(MessageCreateParams.Builder builder) {
		try {
			Message response = client().messages().create(builder.build());
			return response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(text -> text.text())
				.collect(Collectors.joining());
		} catch (AnthropicException e) {
			// 내용은 남기지 않고 오류 종류만 기록한다.
			log.warn("LLM 호출에 실패했습니다: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.CHATBOT_UNAVAILABLE);
		}
	}

	private static Base64ImageSource.MediaType mediaType(String contentType) {
		return "image/png".equals(contentType) ? Base64ImageSource.MediaType.IMAGE_PNG : Base64ImageSource.MediaType.IMAGE_JPEG;
	}

	private AnthropicClient client() {
		AnthropicClient local = client;
		if (local == null) {
			synchronized (this) {
				if (client == null) {
					client = AnthropicOkHttpClient.builder()
						.apiKey(properties.apiKey())
						.timeout(Duration.ofSeconds(properties.timeoutSeconds()))
						.maxRetries(1)
						.build();
				}
				local = client;
			}
		}
		return local;
	}

	private static OutputConfig.Effort effort(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "low" -> OutputConfig.Effort.LOW;
			case "medium" -> OutputConfig.Effort.MEDIUM;
			case "high" -> OutputConfig.Effort.HIGH;
			case "xhigh" -> OutputConfig.Effort.XHIGH;
			case "max" -> OutputConfig.Effort.MAX;
			default -> null;
		};
	}

}
