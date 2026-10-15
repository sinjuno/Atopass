package com.atopy.Atopass.domain.record.service;

/** 조회용 사진의 원본 바이트와 실제 형식(Content-Type). */
public record PhotoContent(byte[] bytes, String contentType) {
}
