package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.util.Optional;

/** 상황 질문 조건을 판정하는 데 필요한 정보. 오늘 초안과 어제 기록. */
record SituationalQuestionContext(ChatDraft draft, Optional<SkinRecord> yesterday) {
}
