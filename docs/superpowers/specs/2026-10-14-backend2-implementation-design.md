# 백2(Backend 2) 구현 설계

- 작성일: 2026-10-14
- 작성자: 백2 담당자 (이 세션의 사용자)
- 관련 레포: https://github.com/sinjuno/Skinlog.git (브랜치: `develop` 기준)

## 1. 배경

Skinlog(아토피 피부 기록 서비스)는 4인 팀이 역할을 나눠 개발한다.

- 프1: 로그인, 홈, 그래프, 진료 참고용 기록 (프론트)
- 프2: 챗봇, 일일 총정리 (프론트)
- 백1: 공통(global) + 인증(JWT·리프레시) + 피부 기록 CRUD + 챗봇(LLM) + 분석 요약 + 보고서 파일 생성
- **백2(이 문서의 범위): 내 정보 + 사진 + 분석 그래프·날짜 비교 + 보고서 목록·상세**

팀 전체가 이미 다음 산출물을 합의해둔 상태이며, 이 문서는 그 산출물을 재설계하지 않고 **백2 영역을 어떻게 구현할지**만 다룬다.

- 정보구조도(IA), 기능명세서 v1.0, `openapi.yaml`(API 계약), 모노레포 아키텍처, 기술 스택(Java 21 / Spring Boot 3.x / Gradle / MySQL / Redis / JWT / SpringDoc)

현재 레포 상태: `backend/`는 Gradle 스캐폴드만 있고 Java 코드가 전혀 없다. `docs/API.md`, `docs/erd.dbml`, `docs/openapi.yaml`도 빈 파일이다. 즉 백1 쪽 코드(공통 응답 포맷, 인증, 회원/기록 엔티티)가 아직 하나도 존재하지 않는다.

## 2. 진행 방식 (확정)

- **독립 개발**: 백1의 코드가 올라올 때까지 기다리지 않는다. 백2가 의존하는 최소한의 타입(인증 주체, Member/Record 엔티티)을 **스텁**으로 직접 만들어서 컴파일·테스트가 되게 하고, 나중에 백1의 실제 구현과 머지하면서 교체한다.
- **브랜치 전략**: 기능 단위로 `feature/*` 브랜치를 파서 커밋하고, `develop`으로 PR을 올린다. 병렬이 아니라 아래 순서로 하나씩 진행한다.
- **원격 저장소**: `origin`이 이미 `sinjuno/Skinlog.git`를 가리키고 `develop`을 추적 중이므로 별도 remote 설정 불필요.

## 3. 범위

### 3.1 포함 (백2가 구현)

`openapi.yaml` 기준 매핑:

| 기능 | 엔드포인트 | 기능명세서 ID |
| --- | --- | --- |
| 내 정보 조회/수정 | `GET /members/me`, `PATCH /members/me` | (회원 관련, 명세서 "마이페이지") |
| 사진 업로드 | `POST /records/{recordId}/photos` | REC-04, FILE-01 |
| 사진 조회 | `GET /records/{recordId}/photos/{photoId}` | FILE-02 |
| 사진 삭제 | `DELETE /records/{recordId}/photos/{photoId}` | FILE-01 연장 |
| 분석 그래프 | `GET /analysis/trends` | VIS-01~03 |
| 날짜별 기록 비교 | `GET /analysis/comparison` | ANA-06 |
| 보고서 목록 | `GET /reports` | REP 영역 중 목록 |
| 보고서 상세 | `GET /reports/{reportId}` | REP 영역 중 상세 |

### 3.2 제외 (다른 담당자 영역, 백2는 스텁으로만 가정)

- 회원가입/로그인/로그아웃/토큰 재발급, JWT 발급·검증 (백1)
- 피부 기록 생성/조회/수정/삭제 자체 (`/records`, `/records/{id}`) (백1)
- 챗봇 전체 (백1)
- `GET /analysis/summary` (분석 요약, 백1)
- `POST /reports`(보고서 생성·PDF 생성), `GET /reports/{reportId}/file`(PDF 다운로드) (백1)
- `global` 패키지의 영구 버전(공통 응답/예외 처리)도 원칙적으로 백1 책임이지만, 부트스트랩 단계에서 백2가 임시로 최소 버전을 만든다 (4.1 참조).

## 4. 구현 계획

### 4.1 부트스트랩 (`chore/backend-bootstrap`) — 선행 작업, develop에 먼저 PR

다른 모든 기능 브랜치가 이 위에서 작업하므로 가장 먼저 머지되어야 한다.

- Spring Boot 3.x 애플리케이션 골격 (`com.atopy.service`), `application.yml` (MySQL 연결 정보는 환경변수/placeholder, 테스트는 H2)
- `global` 패키지: `ApiResponse`, `ErrorResponse`, `ErrorDetail`, `ErrorCode`, `GlobalExceptionHandler` — `openapi.yaml`의 `SuccessBase`/`ErrorResponse` 스키마와 공통 에러 코드(`INVALID_INPUT`, `UNAUTHORIZED`, `RECORD_NOT_FOUND` 등)를 그대로 구현
  - **스텁 표시**: 클래스 상단에 `// TEMP STUB — 백1 공통 모듈 머지 시 교체` 주석으로 임시임을 명시
- 인증 스텁: 실제 JWT 대신, 요청 헤더 `X-Member-Id`를 신뢰하는 `TempAuthFilter` + `@AuthenticationPrincipal`로 주입되는 `AuthenticatedMember(memberId)`. 헤더가 없으면 `401 UNAUTHORIZED`.
- `PasswordEncoder`(bcrypt) Bean 등록 — 스텁이 아닌 실제 구현. 백1의 회원가입·로그인도 같은 Bean을 재사용하게 된다.
- 엔티티 스텁 (백1이 실제 구현을 올리면 교체):
  - `Member(id, email, nickname, password)` — `domain/member/entity`
  - `Record(id, memberId, recordDate)` + `SkinCondition`(bodyPart, symptoms Map 또는 @Embeddable) — `domain/record/entity`
- `docs/openapi.yaml`, `docs/API.md`, `docs/erd.dbml`에 이번에 전달받은 자료(스웨거 전문, 기능명세서, 정보구조도 요약, ERD는 엔티티 확정되는 대로)를 반영
- CI: `.github/workflows/backend-ci.yml`에 최소 빌드/테스트 스텝 (이미 파일이 존재하면 내용만 보강)

### 4.2 `feature/be2-member-info`

- `domain/member/controller/MemberController` — `GET/PATCH /members/me`
- `domain/member/service/MemberService`, `dto/request/MemberUpdateRequest`, `dto/response/MemberResponse`
- 수정 가능 필드: `nickname`, `password` — 비밀번호는 보안 사항이라 스텁으로 두지 않고 Spring Security `PasswordEncoder`(bcrypt)로 바로 암호화하여 저장. 회원가입(`SEC-01`, 백1 영역)도 같은 인코더를 쓰게 되므로 이 Bean은 부트스트랩 단계의 공통 설정으로 올린다.
- 테스트: `MemberController` 통합 테스트(H2) — 내 정보 조회, 닉네임 수정, 인증 없음(401)

### 4.3 `feature/be2-photo`

- `domain/record/entity/Photo(id, recordId, storageKey, originalFilename, contentType, size, uploadedAt)` — Record 하위 리소스이므로 `record` 도메인 패키지에 둔다 (별도 `photo` 도메인 생성하지 않음, 아키텍처 문서의 도메인 목록과 일치)
- `PhotoStorage` 인터페이스 (`save(MultipartFile): storageKey`, `load(storageKey): Resource`, `delete(storageKey)`) + `LocalFilePhotoStorage` 구현 (`uploads/photos/` 디렉터리, `.gitignore` 처리)
- 검증: JPEG/PNG만, 파일당 10MB, 기록당 5장 제한, 확장자 아닌 파일 시그니처(매직 바이트)로 형식 검증
- 에러 코드: `INVALID_FILE_TYPE`(400), `PHOTO_LIMIT_EXCEEDED`(409), `FILE_TOO_LARGE`(413), 기록 소유권 불일치 시 `RECORD_NOT_FOUND`(404, 존재 여부 숨김)
- 테스트: 업로드 성공/실패(형식, 크기, 개수 초과), 조회(본인/타인), 삭제

### 4.4 `feature/be2-analysis`

- `domain/analysis/controller/AnalysisController` — `trends`, `comparison` (summary는 만들지 않음)
- `domain/analysis/service/AnalysisService` — Record/SkinCondition을 즉시 조회해 계산 (별도 테이블 없음)
- `trends`: `bodyPart` + `symptom` 쿼리 필수, 기록 없는 날짜는 포인트 생성 안 함
- `comparison`: 두 날짜(A, B) 각각 조회, 기록 없으면 `null`
- 테스트: 기록 있는/없는 날짜 혼합, 기간 경계값

### 4.5 `feature/be2-report`

- `domain/report/entity/Report(id, memberId, startDate, endDate, includePhotos, pdfStorageKey nullable, createdAt)` — 생성 로직(POST, PDF 생성)은 백1 책임이므로 **테이블 스키마만 가정**하고 읽기 전용 기능만 구현
- `domain/report/controller/ReportController` — `GET /reports`(내 보고서 목록), `GET /reports/{id}`(상세, 소유권 확인 후 `404 REPORT_NOT_FOUND`)
- 테스트: 목록 페이징/정렬, 상세 조회, 타인 보고서 접근 시 404

### 4.6 테스트 전략 (전체)

- JUnit5 + MockMvc + H2 인메모리 DB, 기능 브랜치마다 TDD로 작성 (`superpowers:test-driven-development` 적용)
- 인증이 필요한 요청은 `X-Member-Id` 헤더로 테스트 사용자 지정

## 5. 리스크 / 추후 조정 사항

- 백1이 실제 `Member`/`Record`/인증/공통 응답을 머지하면, 백2가 만든 스텁과 필드명·타입이 다를 수 있다 → 머지 시 백2 코드의 참조를 백1 실제 클래스로 교체하는 통합 작업이 필요 (이번 설계 범위 밖, 머지 시점에 별도 처리)
- `Report` 테이블 스키마는 백1의 실제 생성 로직과 100% 일치하지 않을 수 있음 → 머지 시 ERD 재확인 필요
- 파일 저장을 로컬 디스크로 시작하므로, 배포 환경에서는 `PhotoStorage` 구현체 교체가 필요 (설계상 인터페이스로 분리해뒀으므로 영향 최소화)
