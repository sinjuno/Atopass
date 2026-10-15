# Atopass

Atopass는 아토피 피부염 환자가 매일의 피부 상태를 기록하고, 쌓인 기록으로 변화를 확인하고, 진료 때 참고할 수 있는 문서(PDF)로 정리하는 서비스입니다.

- 챗봇과 대화하며 하루 기록을 입력하고, 확인한 뒤 저장합니다. (챗봇은 외부 AI(Claude)를 사용하므로 **처음 쓸 때 이용 동의**가 필요합니다.)
- 부위별 증상 점수(0~10), 수면, 생활습관, 사진, 특이사항을 날짜별로 기록합니다. 기록은 하루 1건입니다.
- 이 서비스는 의료적 진단이나 판단을 대신하지 않습니다. 점수는 사용자가 느낀 주관적인 정도입니다.

## 구성

모노레포입니다.

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot 4 · Java 21 · JPA · MySQL · JWT 인증 |
| `frontend/` | React 19 · TypeScript · Vite · Tailwind |
| `docs/` | API 명세(`Atopass_openapi.yaml`), ERD(`Atopass_erd.dbml`) |

## 로컬에서 실행하기

### 1. 준비물

- JDK 21
- Node.js 24 (CI와 같은 버전)
- MySQL 8

MySQL에 데이터베이스를 만들어 둡니다.

```sql
CREATE DATABASE atopass CHARACTER SET utf8mb4;
```

테이블은 서버가 처음 뜰 때 자동으로 만들어집니다(`JPA_DDL_AUTO=update`).

### 2. 환경변수

비밀 값은 **코드나 레포에 넣지 말고 환경변수로만** 넣습니다.

| 이름 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `JWT_SECRET` | 필수 | 없음 | 로그인 토큰 서명 키. **32바이트 이상**이어야 서버가 뜹니다. |
| `DB_URL` | | `jdbc:mysql://localhost:3306/atopass?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | DB 주소 |
| `DB_USERNAME` | | `root` | DB 계정 |
| `DB_PASSWORD` | | (빈 값) | DB 비밀번호 |
| `ANTHROPIC_API_KEY` | | 없음 | 챗봇이 쓰는 Claude API 키. **없으면 챗봇만 503**(`CHATBOT_UNAVAILABLE`)이고 나머지 기능은 동작합니다. |
| `LLM_MODEL` | | `claude-opus-5-5` | 챗봇 모델. 비용을 줄이려면 `claude-haiku-5-5` 등으로 바꿉니다. |
| `LLM_EFFORT` | | `low` | 챗봇 응답 깊이 (`low`, `medium`, `high`, `xhigh`, `max`) |
| `CHATBOT_DAILY_LIMIT` | | `100` | 회원 한 명이 최근 24시간 동안 보낼 수 있는 챗봇 메시지 수. 넘으면 429(`CHAT_LIMIT_EXCEEDED`). LLM 비용 보호용입니다. |
| `COOKIE_SECURE` | | `true` | 리프레시 토큰 쿠키의 `Secure` 속성. Safari로 `http://localhost`에서 개발할 때만 `false` |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:5173` | 허용할 프론트 주소 |
| `REPORT_DIR` | | `./data/reports` | 보고서 PDF 저장 폴더. 웹으로 공개되는 경로 밖이어야 합니다. |
| `PHOTO_DIR` | | `./data/photos` | 기록 사진 저장 폴더. 웹으로 공개되는 경로 밖이어야 합니다. |
| `JPA_DDL_AUTO` | | `update` | 테이블 자동 생성·변경 방식 |

### 3. 백엔드

```bash
cd backend
# 예: Windows PowerShell
#   $env:JWT_SECRET = "32바이트-이상의-임의의-문자열을-넣으세요-1234"
#   $env:DB_PASSWORD = "내-DB-비밀번호"
#   $env:ANTHROPIC_API_KEY = "챗봇을 쓸 때만"
./gradlew bootRun
```

서버는 `http://localhost:8080`에서 뜹니다. API는 모두 `/api/v1` 아래에 있습니다.

### 4. 프론트엔드

```bash
cd frontend
npm ci
npm run dev
```

`http://localhost:5173`에서 열립니다. 개발 서버가 `/api` 요청을 `localhost:8080`으로 넘겨 주기 때문에(같은 출처), 리프레시 토큰 쿠키가 정상적으로 전달됩니다.

### 5. 테스트

```bash
cd backend && ./gradlew test     # 임시 DB(H2)를 쓰고, 실제 AI는 호출하지 않습니다(가짜 LLM)
cd frontend && npm run build     # 타입 검사(tsc)와 빌드
```

## 문서

- API 명세: `docs/Atopass_openapi.yaml` — [Swagger Editor](https://editor.swagger.io/)에 붙여넣거나 Swagger UI로 볼 수 있습니다. 프론트와 백엔드의 약속(계약)이므로, 필드를 바꿀 때는 코드보다 먼저 이 파일을 고칩니다.
- ERD: `docs/Atopass_erd.dbml` — [dbdiagram.io](https://dbdiagram.io/)에 붙여넣으면 그림으로 보입니다.
- 배포: [`docs/DEPLOY.md`](docs/DEPLOY.md) — Oracle 무료 VM + Docker로 올리는 방법과, `main` 머지 시 자동 배포되는 흐름

## 개발 규칙

- 기능 하나마다 **이슈 → feature 브랜치 → `develop`으로 PR** 순서로 진행합니다. 브랜치 이름이 겹치지 않게, 백엔드 브랜치는 끝에 `-api`를 붙입니다. (예: `feature/chatbot-api`)
- PR 템플릿(`.github/pull_request_template.md`)을 채웁니다. 백엔드·프론트 PR은 CI(`Backend CI`, `Frontend CI`)가 통과해야 합니다.
- 다른 PR 위에 쌓은 PR은 **"Create a merge commit"**으로 머지하고, 앞 PR을 머지한 뒤 다음 PR의 base를 `develop`으로 바꿔서 머지합니다. **앞 PR의 브랜치를 먼저 지우면 다음 PR이 자동으로 닫힙니다.**
- 충돌을 해결할 때는 이미 `develop`에 머지된 다른 사람의 화면·코드를 덮어쓰지 않도록, 충돌 파일마다 어느 쪽이 완성본인지 확인합니다.
