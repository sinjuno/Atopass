package com.atopy.Atopass.domain.record.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/** 증상 종류. JSON 키는 소문자(itching 등)로 주고받는다. */
public enum Symptom {
	@JsonProperty("itching") ITCHING("가려움"),
	@JsonProperty("stinging") STINGING("따가움"),
	@JsonProperty("redness") REDNESS("붉어짐"),
	@JsonProperty("dryness") DRYNESS("건조함"),
	@JsonProperty("flaking") FLAKING("각질"),
	@JsonProperty("swelling") SWELLING("부기"),
	@JsonProperty("pain") PAIN("통증");

	private final String label;

	Symptom(String label) {
		this.label = label;
	}

	/** 사용자에게 보여줄 한글 이름 */
	public String label() {
		return label;
	}

	/** API 파라미터(itching 등)로 받은 값을 변환한다. 대소문자는 구분하지 않는다. */
	public static Symptom fromKey(String key) {
		return valueOf(key.trim().toUpperCase(Locale.ROOT));
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
