package com.atopy.Atopass.domain.record.event;

/**
 * 기록이 삭제될 때 발행한다. 사진 파일 정리처럼 기록 삭제에 연동해야 하는 쪽에서
 * {@code @TransactionalEventListener}로 받아 처리한다.
 */
public record RecordDeletedEvent(Long recordId, Long memberId) {
}
