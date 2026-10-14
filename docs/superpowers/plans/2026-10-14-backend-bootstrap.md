# Backend Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `backend/` Spring Boot app actually boot and test, and add the minimal shared pieces (common response/exception format, header-based temp auth, Member/Record stub entities) that every later 백2 feature branch depends on.

**Architecture:** Single Spring Boot 4.1.1 / Java 21 monolith (`com.atopy.Skinlog`), MySQL in real profiles, H2 in tests. Auth is a temporary header-based stub (`X-Member-Id`) until 백1 ships real JWT. Entities created here (`Member`, `Record`, `SkinCondition`) are intentionally minimal stand-ins for 백1's real domain objects.

**Tech Stack:** Spring Boot 4.1.1, Spring Data JPA, Spring Security (for `PasswordEncoder` only), Spring Validation, Lombok, MySQL connector, H2 (test), JUnit 5, Gradle (toolchain Java 21).

**Spec:** `docs/superpowers/specs/2026-10-14-backend2-implementation-design.md` (section 4.1 "부트스트랩")

## Global Constraints

- Root package is `com.atopy.Skinlog` (capital S) — this is what every existing domain folder under `backend/src/main/java/com/atopy/Skinlog/...` already uses. (See Task 1 — the current `ServiceApplication` does NOT match this and must be fixed first, or nothing below it gets component-scanned.)
- Java 21, Gradle toolchain already pinned in `backend/build.gradle` — do not change the Spring Boot version or starter artifact ids already present there (they are correct for Spring Boot 4.1.x's modularized starters, e.g. `spring-boot-starter-webmvc` and the per-starter `*-test` artifacts are real in this version — verified, not a typo).
- All JSON success responses use `ApiResponse<T>{success, message, data}`; all error responses use `ErrorResponse{success:false, error:{code, message, details}}` — exact shape from `docs/openapi.yaml`'s `SuccessBase`/`ErrorResponse` schemas. Every controller written in later branches must return these, so the shapes decided in Task 2 are load-bearing for the whole 백2 scope.
- Error codes are exactly the set enumerated in `docs/openapi.yaml`'s `ErrorResponse.error.code` description — do not invent new codes outside that list without updating the spec.
- Auth stub reads header `X-Member-Id` (a `Long`); missing or non-numeric → `401 UNAUTHORIZED`. This header name is what every later 백2 controller test will send.
- Lombok `@Getter` + protected no-args constructor + a public all-args-ish domain constructor is the entity style (no `@Setter` on entities; mutate via explicit methods) — matches the "mutate through well-defined interfaces" guidance in the spec.

## Review Focus

- A request to any `@CurrentMember`-annotated endpoint with no `X-Member-Id` header must get `401` with the standard `ErrorResponse` body, not a generic Spring 403/500 page — Task 3 covers this.
- A request with a non-numeric `X-Member-Id` (e.g. `X-Member-Id: abc`) must also get `401`, not a 500 from an uncaught `NumberFormatException` — Task 3 covers this.
- A `@Valid`-annotated request body that fails validation must come back as `400 INVALID_INPUT` with a populated `details` array (field + reason), not Spring's default validation error page — Task 2 covers this.
- Saving a `Record` with multiple `SkinCondition`s and reloading it must return all of them with the parent association intact (cascade/orphanRemoval configured correctly), since every later analysis/photo query depends on this — Task 5 covers this.
- `Member.email` must be enforced unique at the DB level (not just assumed), since 백1's real signup will rely on the same column — Task 4 covers this.

---

## Task 1: Fix the package mismatch and get the app booting in tests

The existing `ServiceApplication.java` lives under `backend/src/main/java/com/atopy/Skinlog/` but declares `package com.atopy.service;`. Every domain/global folder already created in the repo is a sub-package of `com.atopy.Skinlog`. Because `@SpringBootApplication` only component-scans its own package and below, nothing under `Skinlog.*` would ever be picked up until this is fixed. There is also no test datasource configured yet, so even a correctly-packaged smoke test would fail trying to auto-configure a MySQL `DataSource`.

**Files:**
- Modify: `backend/src/main/java/com/atopy/Skinlog/ServiceApplication.java`
- Move + modify: `backend/src/test/java/com/atopy/service/ServiceApplicationTests.java` → `backend/src/test/java/com/atopy/Skinlog/ServiceApplicationTests.java`
- Modify: `backend/build.gradle` (add H2 test dependency)
- Modify: `backend/src/main/resources/application.yml` (add MySQL datasource via env vars, so `bootRun` works outside tests)
- Create: `backend/src/test/resources/application.yml`

**Interfaces:**
- Produces: nothing other tasks call directly, but every later `@SpringBootTest`/`@DataJpaTest`/`@WebMvcTest` in this plan relies on the test datasource configured here existing and working.

- [ ] **Step 1: Run the existing test to confirm it currently fails/misbehaves**

Run: `cd backend && ./gradlew test --tests "com.atopy.service.ServiceApplicationTests"`
Expected: either a compile/package resolution problem, or (if it compiles) a context-load failure from Spring Boot trying to auto-configure a real `DataSource` with no DB available.

- [ ] **Step 2: Fix the package declaration**

In `backend/src/main/java/com/atopy/Skinlog/ServiceApplication.java`, change `package com.atopy.service;` to `package com.atopy.Skinlog;`. Leave the class body as-is.

- [ ] **Step 3: Move the test to match**

Move `backend/src/test/java/com/atopy/service/ServiceApplicationTests.java` to `backend/src/test/java/com/atopy/Skinlog/ServiceApplicationTests.java`, changing its `package` declaration to `com.atopy.Skinlog` to match. Delete the now-empty `backend/src/test/java/com/atopy/service/` directory.

- [ ] **Step 4: Add the H2 test dependency**

In `backend/build.gradle`, add under the existing `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` line:
```groovy
testRuntimeOnly 'com.h2database:h2'
```

- [ ] **Step 5: Add a real (non-test) MySQL datasource**

`backend/src/main/resources/application.yml` currently only has `spring.application.name`, so `./gradlew bootRun` has nothing to connect to. Add datasource config via env vars (with local-dev defaults so it still runs without any env vars set):
```yaml
spring:
  application:
    name: service
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:skinlog}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```
(`ddl-auto: validate` here, not `create`/`update` — schema changes belong in migrations the team will add later, not implicit Hibernate auto-DDL in the real DB. Tests still use `create-drop` against H2, configured next.)

- [ ] **Step 6: Add a test datasource**

Create `backend/src/test/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:skinlog;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
```

- [ ] **Step 7: Run the test again and verify it passes**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.ServiceApplicationTests"`
Expected: PASS (`contextLoads` succeeds against the H2 datasource).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/atopy/Skinlog/ServiceApplication.java backend/src/test/java/com/atopy/Skinlog/ServiceApplicationTests.java backend/build.gradle backend/src/main/resources/application.yml backend/src/test/resources/application.yml
git commit -m "fix: align root package to com.atopy.Skinlog, add real and test datasources"
```

---

## Task 2: Common response/exception format

**Files:**
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/exception/ErrorCode.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/exception/BusinessException.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/response/ApiResponse.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/response/ErrorResponse.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/response/ErrorDetail.java`
- Test: `backend/src/test/java/com/atopy/Skinlog/global/exception/GlobalExceptionHandlerTest.java`
- Test support (test-only, not shipped): `backend/src/test/java/com/atopy/Skinlog/global/support/ValidationTestController.java`
- Test: `backend/src/test/java/com/atopy/Skinlog/global/support/ValidationTestControllerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (every later task/branch uses these exact names):
  - `ApiResponse<T>` — record with `boolean success`, `String message`, `T data`; static factories `ApiResponse.of(T data)` (default message `"요청이 성공했습니다."`) and `ApiResponse.of(String message, T data)`.
  - `ErrorCode` — enum with `int getStatus()`, `String getMessage()`. Members (name → status): `INVALID_INPUT`=400, `UNAUTHORIZED`=401, `FORBIDDEN`=403, `INTERNAL_ERROR`=500, `INVALID_CREDENTIALS`=401, `TOKEN_EXPIRED`=401, `EMAIL_DUPLICATED`=409, `RECORD_NOT_FOUND`=404, `RECORD_ALREADY_EXISTS`=409, `INVALID_SCORE_RANGE`=400, `INVALID_FILE_TYPE`=400, `FILE_TOO_LARGE`=413, `PHOTO_LIMIT_EXCEEDED`=409, `SESSION_NOT_FOUND`=404, `SESSION_CLOSED`=409, `CHATBOT_UNAVAILABLE`=503, `DRAFT_NOT_CONFIRMED`=400, `REPORT_NOT_FOUND`=404, `NO_RECORDS_IN_PERIOD`=400.
  - `BusinessException` — `extends RuntimeException`; constructors `BusinessException(ErrorCode errorCode)` and `BusinessException(ErrorCode errorCode, List<ErrorDetail> details)`; `getErrorCode()`, `getDetails()` (empty list if none given).
  - `ErrorDetail` — record `ErrorDetail(String field, String reason)`.
  - `ErrorResponse` — record `ErrorResponse(boolean success, ErrorBody error)` with nested `record ErrorBody(String code, String message, List<ErrorDetail> details)`; static factory `ErrorResponse.of(ErrorCode code, List<ErrorDetail> details)`.
  - `GlobalExceptionHandler` (`@RestControllerAdvice`) — `handleBusinessException(BusinessException e): ResponseEntity<ErrorResponse>` and `handleValidationException(MethodArgumentNotValidException e): ResponseEntity<ErrorResponse>` (maps each field error to an `ErrorDetail`, responds `400 INVALID_INPUT`).

- [ ] **Step 1: Write the failing unit test for the business-exception path**

```java
// backend/src/test/java/com/atopy/Skinlog/global/exception/GlobalExceptionHandlerTest.java
package com.atopy.Skinlog.global.exception;

import com.atopy.Skinlog.global.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessException_mapsToItsErrorCodeStatusAndBody() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("RECORD_NOT_FOUND");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.exception.GlobalExceptionHandlerTest"`
Expected: FAIL (classes/methods above don't exist yet).

- [ ] **Step 3: Implement `ErrorCode`, `ErrorDetail`, `BusinessException`, `ApiResponse`, `ErrorResponse`, `GlobalExceptionHandler`**

Use the exact names, fields, and `ErrorCode` members/statuses listed in this task's Interfaces block. `GlobalExceptionHandler.handleBusinessException` returns `ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.of(e.getErrorCode(), e.getDetails()))`.

- [ ] **Step 4: Run the test again and verify it passes**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.exception.GlobalExceptionHandlerTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the validation path (needs a real MVC round-trip)**

```java
// backend/src/test/java/com/atopy/Skinlog/global/support/ValidationTestController.java
package com.atopy.Skinlog.global.support;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
class ValidationTestController {
    @PostMapping("/__test/validate")
    String echo(@org.springframework.validation.annotation.Validated @org.springframework.web.bind.annotation.RequestBody Body body) {
        return body.name();
    }
    record Body(@NotBlank String name) {}
}
```

```java
// backend/src/test/java/com/atopy/Skinlog/global/support/ValidationTestControllerTest.java
package com.atopy.Skinlog.global.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(ValidationTestController.class)
class ValidationTestControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void blankName_returns400WithDetails() throws Exception {
        mockMvc.perform(post("/__test/validate").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.support.ValidationTestControllerTest"`
Expected: FAIL (`GlobalExceptionHandler` has no `MethodArgumentNotValidException` handler yet, so Spring's default error body comes back instead).

- [ ] **Step 7: Implement `handleValidationException`**

In `GlobalExceptionHandler`, add `@ExceptionHandler(MethodArgumentNotValidException.class) handleValidationException(MethodArgumentNotValidException e)`: build one `ErrorDetail(field, reason)` per `e.getBindingResult().getFieldErrors()` (`fieldError.getField()`, `fieldError.getDefaultMessage()`), return `400` with `ErrorResponse.of(ErrorCode.INVALID_INPUT, details)`.

- [ ] **Step 8: Run both tests again and verify they pass**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.exception.GlobalExceptionHandlerTest" --tests "com.atopy.Skinlog.global.support.ValidationTestControllerTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/atopy/Skinlog/global backend/src/test/java/com/atopy/Skinlog/global
git commit -m "feat: implement common ApiResponse/ErrorResponse and global exception handling"
```

---

## Task 3: Header-based temp auth (`@CurrentMember`) + `PasswordEncoder`

**Files:**
- Create: `backend/src/main/java/com/atopy/Skinlog/global/auth/AuthenticatedMember.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/global/auth/CurrentMember.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/global/auth/CurrentMemberArgumentResolver.java`
- Modify: `backend/src/main/java/com/atopy/Skinlog/global/config/WebConfig.java` (new file in the existing, currently-empty `global/config` folder)
- Create: `backend/src/main/java/com/atopy/Skinlog/global/config/PasswordEncoderConfig.java`
- Test: `backend/src/test/java/com/atopy/Skinlog/global/auth/CurrentMemberArgumentResolverTest.java`

**Interfaces:**
- Consumes: `BusinessException`, `ErrorCode.UNAUTHORIZED` (Task 2).
- Produces (every later 백2 controller uses this exact pattern: `@GetMapping(...) response(@CurrentMember AuthenticatedMember me)`):
  - `AuthenticatedMember` — record `AuthenticatedMember(Long memberId)`.
  - `@CurrentMember` — parameter annotation, `@Target(ElementType.PARAMETER)`, `@Retention(RUNTIME)`.
  - `CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver` — registered globally via `WebConfig`.
  - `PasswordEncoderConfig` — `@Bean PasswordEncoder passwordEncoder()` returning `new BCryptPasswordEncoder()`.

- [ ] **Step 1: Write the failing tests**

```java
// backend/src/test/java/com/atopy/Skinlog/global/auth/CurrentMemberArgumentResolverTest.java
package com.atopy.Skinlog.global.auth;

import com.atopy.Skinlog.global.exception.BusinessException;
import com.atopy.Skinlog.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @Test
    void validHeader_resolvesAuthenticatedMember() throws Exception {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("7");

        AuthenticatedMember result = (AuthenticatedMember) resolver.resolveArgument(null, null, request, null);

        assertThat(result.memberId()).isEqualTo(7L);
    }

    @Test
    void missingHeader_throwsUnauthorized() {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void nonNumericHeader_throwsUnauthorized() {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("not-a-number");

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.auth.CurrentMemberArgumentResolverTest"`
Expected: FAIL (classes don't exist).

- [ ] **Step 3: Implement `AuthenticatedMember`, `@CurrentMember`, `CurrentMemberArgumentResolver`, `WebConfig`, `PasswordEncoderConfig`**

`CurrentMemberArgumentResolver.supportsParameter` returns true when the parameter has `@CurrentMember` and type `AuthenticatedMember`. `resolveArgument` reads header `X-Member-Id`; `null` or `NumberFormatException` on parse → `throw new BusinessException(ErrorCode.UNAUTHORIZED)`; otherwise `new AuthenticatedMember(Long.parseLong(header))`. `WebConfig implements WebMvcConfigurer`, injects `CurrentMemberArgumentResolver` and adds it in `addArgumentResolvers`.

- [ ] **Step 4: Run tests again and verify pass**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.global.auth.CurrentMemberArgumentResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/atopy/Skinlog/global/auth backend/src/main/java/com/atopy/Skinlog/global/config backend/src/test/java/com/atopy/Skinlog/global/auth
git commit -m "feat: add header-based temp auth stub and PasswordEncoder bean"
```

---

## Task 4: `Member` stub entity + repository

**Files:**
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/member/entity/Member.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/member/repository/MemberRepository.java`
- Test: `backend/src/test/java/com/atopy/Skinlog/domain/member/repository/MemberRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (plain JPA).
- Produces (used by `feature/be2-member-info` and by `Record`'s `memberId` conceptually, though there is no FK constraint — see spec §4.1):
  - `Member` — fields `id (Long)`, `email (String, unique, not null)`, `nickname (String, not null)`, `password (String, not null)`; constructor `Member(String email, String nickname, String password)`; `updateNickname(String nickname)`, `updatePassword(String encodedPassword)`.
  - `MemberRepository extends JpaRepository<Member, Long>` — `Optional<Member> findByEmail(String email)`.

- [ ] **Step 1: Write the failing repository test**

```java
// backend/src/test/java/com/atopy/Skinlog/domain/member/repository/MemberRepositoryTest.java
package com.atopy.Skinlog.domain.member.repository;

import com.atopy.Skinlog.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MemberRepositoryTest {

    @Autowired MemberRepository memberRepository;

    @Test
    void saveAndFindByEmail_roundTrips() {
        memberRepository.save(new Member("user@example.com", "사용자", "encoded-password"));

        var found = memberRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("사용자");
    }

    @Test
    void duplicateEmail_violatesUniqueConstraint() {
        memberRepository.save(new Member("dup@example.com", "닉네임1", "pw1"));
        memberRepository.flush();

        assertThatThrownBy(() -> {
            memberRepository.save(new Member("dup@example.com", "닉네임2", "pw2"));
            memberRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.domain.member.repository.MemberRepositoryTest"`
Expected: FAIL (`Member`/`MemberRepository` don't exist).

- [ ] **Step 3: Implement `Member` and `MemberRepository`**

`@Entity`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, `email` column with `unique = true, nullable = false`. Lombok `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on the entity (per Global Constraints — no `@Setter`).

- [ ] **Step 4: Run again and verify pass**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.domain.member.repository.MemberRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/atopy/Skinlog/domain/member backend/src/test/java/com/atopy/Skinlog/domain/member
git commit -m "feat: add Member stub entity and repository"
```

---

## Task 5: `Record` + `SkinCondition` stub entities + repository

**Files:**
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/record/entity/BodyPart.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/record/entity/Record.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/record/entity/SkinCondition.java`
- Create: `backend/src/main/java/com/atopy/Skinlog/domain/record/repository/RecordRepository.java`
- Test: `backend/src/test/java/com/atopy/Skinlog/domain/record/repository/RecordRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (used by `feature/be2-photo` for the `recordId` FK, and by `feature/be2-analysis` to compute trends/comparisons):
  - `BodyPart` enum: `FACE, NECK, CHEST, BACK, ABDOMEN, ARM, HAND, LEG, FOOT`.
  - `Record` — fields `id (Long)`, `memberId (Long)`, `recordDate (LocalDate)`, `skinConditions (List<SkinCondition>)`; constructor `Record(Long memberId, LocalDate recordDate)`; `addSkinCondition(SkinCondition condition)` (sets both sides of the association).
  - `SkinCondition` — fields `id (Long)`, `record (Record)`, `bodyPart (BodyPart)`, nullable `Integer itching, stinging, redness, dryness, flaking, swelling, pain` (each 0–10, matching `docs/openapi.yaml`'s `Symptoms` schema); constructor `SkinCondition(BodyPart bodyPart)`.
  - `RecordRepository extends JpaRepository<Record, Long>` — `List<Record> findByMemberIdAndRecordDateBetween(Long memberId, LocalDate start, LocalDate end)`, `Optional<Record> findByMemberIdAndRecordDate(Long memberId, LocalDate date)`.

- [ ] **Step 1: Write the failing repository test**

```java
// backend/src/test/java/com/atopy/Skinlog/domain/record/repository/RecordRepositoryTest.java
package com.atopy.Skinlog.domain.record.repository;

import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.Record;
import com.atopy.Skinlog.domain.record.entity.SkinCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RecordRepositoryTest {

    @Autowired RecordRepository recordRepository;

    @Test
    void saveWithSkinConditions_roundTripsWithAssociation() {
        Record record = new Record(1L, LocalDate.of(2026, 10, 10));
        SkinCondition arm = new SkinCondition(BodyPart.ARM);
        arm.setItching(4);
        record.addSkinCondition(arm);

        Record saved = recordRepository.save(record);

        Record found = recordRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSkinConditions()).hasSize(1);
        assertThat(found.getSkinConditions().get(0).getBodyPart()).isEqualTo(BodyPart.ARM);
        assertThat(found.getSkinConditions().get(0).getItching()).isEqualTo(4);
    }

    @Test
    void findByMemberIdAndRecordDateBetween_returnsOnlyMatchingRange() {
        recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 1)));
        recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 20)));

        var results = recordRepository.findByMemberIdAndRecordDateBetween(
                1L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 25));

        assertThat(results).hasSize(1);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.domain.record.repository.RecordRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `BodyPart`, `Record`, `SkinCondition`, `RecordRepository`**

`SkinCondition` needs a package-visible or public setter-style mutator for each symptom field (e.g. `setItching(Integer)` etc.) since the plan's test calls `arm.setItching(4)` — either plain setters or a single `recordSymptom(Symptom, Integer)` method if that reads better; keep it consistent with the "mutate via explicit methods" style from Global Constraints. `Record.addSkinCondition` must set `skinCondition`'s back-reference to `this` and add to the list. Cascade: `@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)`.

- [ ] **Step 4: Run again and verify pass**

Run: `cd backend && ./gradlew test --tests "com.atopy.Skinlog.domain.record.repository.RecordRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/atopy/Skinlog/domain/record backend/src/test/java/com/atopy/Skinlog/domain/record
git commit -m "feat: add Record/SkinCondition stub entities and repository"
```

---

## Task 6: CI workflow for backend

**Files:**
- Modify: `.github/workflows/backend-ci.yml` (currently empty)

**Interfaces:**
- Consumes: nothing (runs the test suite built by Tasks 1–5).
- Produces: nothing other tasks depend on.

- [ ] **Step 1: Write the workflow**

```yaml
name: backend-ci

on:
  pull_request:
    branches: [develop, main]
    paths:
      - 'backend/**'
  push:
    branches: [develop, main]
    paths:
      - 'backend/**'

jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - run: chmod +x ./gradlew
      - run: ./gradlew test
```

- [ ] **Step 2: Verify the file is well-formed YAML**

Run: `cd .github/workflows && python -I -c "import yaml,sys; yaml.safe_load(open('backend-ci.yml'))" 2>&1 || Get-Content backend-ci.yml`
Expected: no parse error (if `python`/`pyyaml` aren't available locally, visually confirm indentation is consistent with the block above — this file is verified for real on the next PR, when GitHub Actions actually runs it).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/backend-ci.yml
git commit -m "ci: run backend tests on PR/push to develop and main"
```

---

## Task 7: Sync `docs/openapi.yaml` and `docs/API.md`

The team already agreed on the full OpenAPI contract and 기능명세서 content (both were pasted into the project conversation in full). They currently live nowhere in the repo — `docs/openapi.yaml` and `docs/API.md` are empty. This task copies that already-agreed content in verbatim; no new decisions are made here.

**Files:**
- Create: `docs/openapi.yaml`
- Create: `docs/API.md`
- Modify: `docs/erd.dbml`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing other tasks depend on (reference docs only).

- [ ] **Step 1: Write `docs/openapi.yaml`**

Copy the full OpenAPI 3.0.3 document already shared in the project conversation (title "아토피 피부 기록 서비스 API", paths from `/auth/signup` through `/reports/{reportId}/file`, full `components.schemas`) verbatim into `docs/openapi.yaml`.

- [ ] **Step 2: Write `docs/API.md`**

Copy the 기능명세서 v1.0 tables already shared in the project conversation (sections 1–8: 회원/인증, 피부 기록, 챗봇, 개인화 분석, 그래프/시각화, 리포트, 파일/보안, AI/LLM) verbatim into `docs/API.md` as markdown tables, under a top-level `# 기능명세서 v1.0` heading.

- [ ] **Step 3: Update `docs/erd.dbml`**

Write DBML reflecting exactly the three tables created in Tasks 4–5 (do not invent `photo` or `report` tables yet — those are added by their own feature branches):

```dbml
Table member {
  id bigint [pk, increment]
  email varchar [not null, unique]
  nickname varchar [not null]
  password varchar [not null]
}

Table record {
  id bigint [pk, increment]
  member_id bigint [not null]
  record_date date [not null]
}

Table skin_condition {
  id bigint [pk, increment]
  record_id bigint [not null, ref: > record.id]
  body_part varchar [not null]
  itching int
  stinging int
  redness int
  dryness int
  flaking int
  swelling int
  pain int
}
```

- [ ] **Step 4: Verify the files are non-empty and the YAML parses**

Run: `python -I -c "import yaml; yaml.safe_load(open('docs/openapi.yaml', encoding='utf-8'))"`
Expected: no error. If `python`/`pyyaml` isn't available, open the file and confirm it starts with `openapi: 3.0.3` and ends with the `security:` block.

- [ ] **Step 5: Commit**

```bash
git add docs/openapi.yaml docs/API.md docs/erd.dbml
git commit -m "docs: sync agreed OpenAPI spec, 기능명세서, and ERD into the repo"
```

---

## After all tasks

Push the branch and open the PR to `develop`:

```bash
git push -u origin chore/backend-bootstrap
gh pr create --base develop --title "chore: backend bootstrap (common response, temp auth, stub entities)" --body "..."
```
