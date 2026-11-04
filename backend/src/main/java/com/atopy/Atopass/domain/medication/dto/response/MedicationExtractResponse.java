package com.atopy.Atopass.domain.medication.dto.response;

import java.util.List;

/** 약 봉투 사진에서 읽은 약 목록. 저장하지 않은 값이다. */
public record MedicationExtractResponse(List<Item> items) {

	/**
	 * 읽은 약 한 개. id는 없고, startDate·endDate·memo는 항상 null, isOtherCondition은 항상 false다.
	 * 읽지 못했거나 확실하지 않은 값은 추측하지 않고 null(시간대는 빈 배열)로 둔다.
	 *
	 * @param days 처방 일수
	 */
	public record Item(
		String name,
		String type,
		String dose,
		List<String> times,
		String timesText,
		Integer days,
		String startDate,
		String endDate,
		String memo,
		boolean isOtherCondition
	) {

		public static Item of(String name, String type, String dose, List<String> times, String timesText, Integer days) {
			return new Item(name, type, dose, times, timesText, days, null, null, null, false);
		}

	}

}
