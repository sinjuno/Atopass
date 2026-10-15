package com.atopy.Atopass.domain.report.service;

/**
 * 보고서에 넣을 피부 사진 한 장.
 *
 * @param contentType image/jpeg 또는 image/png
 */
public record ReportPhoto(String contentType, byte[] data) {
}
