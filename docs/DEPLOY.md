# 배포 가이드 (AWS EC2 또는 Oracle 무료 VM + Docker)

화면(React)과 API(Spring Boot)를 **하나의 Docker 이미지**로 묶어 VM 한 대에서 실행합니다.
VM에서는 앱, MySQL, HTTPS(Caddy) 세 개가 `docker compose`로 같이 돌아갑니다.

`main` 브랜치에 머지하면 **테스트 → 이미지 빌드 → 서버 배포**가 자동으로 실행됩니다.
(`develop`에 머지하는 것만으로는 배포되지 않습니다.)

```
인터넷 ──HTTPS──▶ Caddy ──▶ 앱(화면 + API) ──▶ MySQL
                                  │
                              디스크(사진·PDF)
```

## 0. 준비물

- 서버를 빌릴 계정: **AWS 계정**(권장) 또는 Oracle Cloud 계정. 둘 다 가입에 신용카드가 필요합니다.
- 도메인 (직접 구입한 것)
- GitHub 저장소 관리자 권한 (Secrets 등록)
- Anthropic API 키 (챗봇용. 없으면 챗봇만 동작하지 않음)

## 1. 서버 만들기

둘 중 하나를 고릅니다. 이 문서의 나머지 단계는 **어느 쪽이든 똑같습니다.**

### AWS EC2로 만들 때 (권장)

1. AWS 콘솔 오른쪽 위에서 리전을 **서울(ap-northeast-2)**로 고릅니다.
2. **EC2 → Launch instance**
   - 이름: `atopass`
   - 이미지(AMI): **Ubuntu Server 24.04 LTS**
   - 인스턴스 유형: **t3.small**(메모리 2GB, 일반 x86) 또는 **t4g.small**(2GB, ARM, 조금 더 저렴). 이 서비스는 앱(자바)과 MySQL을 같이 돌리므로 **메모리 1GB짜리(t3.micro, t4g.micro)는 부족해서 권장하지 않습니다.**
   - 키 페어: **새로 만들기(.pem)**. 개인키 파일을 안전한 곳에 보관합니다. (분실하면 접속할 수 없습니다)
   - 네트워크 설정(보안 그룹): SSH(22)는 **내 IP만**, HTTP(80)와 HTTPS(443)는 **어디서나(0.0.0.0/0)** 허용
   - 스토리지: **gp3 20~30GB**
3. 생성이 끝나면 **EC2 → Elastic IP → 할당(Allocate)** 후 인스턴스에 **연결(Associate)**합니다. 연결하지 않으면 서버를 껐다 켤 때 공용 IP가 바뀌어 도메인이 끊깁니다.
4. **비용 알림을 꼭 설정합니다.** Billing → Budgets → Create budget → 월 예산 10달러(예시) 초과 시 이메일 알림. 카드를 등록해 두었기 때문에 실수로 큰 비용이 나가는 것을 막는 안전장치입니다.
5. 로그인 계정은 `ubuntu`입니다. 서버 안 방화벽 설정은 필요 없습니다. (보안 그룹이 대신합니다)

> **비용 안내:** 신규 계정은 6개월 또는 크레딧 소진 중 먼저 오는 때까지 무료 플랜을 쓸 수 있습니다. 그 뒤에는 서버 요금(t3.small 월 15~20달러 안팎), 공용 IP 요금(IP 하나당 월 약 3.6달러), 디스크 요금이 청구됩니다. 정확한 금액은 AWS 요금 계산기에서 확인하세요.

### Oracle Cloud로 만들 때

1. Oracle Cloud 콘솔 → **Compute → Instances → Create instance**
2. 이미지: **Ubuntu 24.04** (또는 22.04), Shape: **Ampere (VM.Standard.A1.Flex)** — 무료 한도 안에서 2 OCPU / 12GB 이상을 고릅니다.
3. 네트워크: 공용 IP 할당(Assign a public IPv4 address)을 켭니다.
4. SSH 키: **Generate a key pair**로 만들고 개인키(`.key`)를 안전한 곳에 저장합니다. (분실하면 접속할 수 없습니다)
5. 생성이 "Out of capacity"로 실패하면 시간을 두고 다시 시도하거나 다른 가용 도메인(AD)을 고릅니다.

#### 포트 열기 (80, 443)

두 군데를 모두 열어야 합니다.

**① Oracle 콘솔:** 인스턴스 → Subnet → Security List → **Add Ingress Rules**
- Source CIDR `0.0.0.0/0`, TCP, Destination Port `80`
- 같은 방식으로 `443`

**② VM 안의 방화벽** (Oracle Ubuntu 이미지는 기본으로 막혀 있습니다)
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 2. 포트 열기 확인 (80, 443)

- **AWS:** 1단계의 보안 그룹 설정으로 끝났습니다. 서버 안 방화벽 작업은 없습니다.
- **Oracle:** 1단계의 "포트 열기"(콘솔 + 서버 안 방화벽 두 군데)를 했는지 확인합니다.

## 3. 도메인 연결

도메인 관리 화면에서 **A 레코드**를 만들어 VM의 공용 IP로 연결합니다.
(예: `atopass.example.com` → `123.45.67.89`) 반영에 몇 분~수 시간이 걸릴 수 있습니다.
HTTPS 인증서는 도메인이 VM을 가리키면 Caddy가 자동으로 발급합니다.

## 4. VM 초기 설정 (한 번만)

내 PC에서 접속합니다. (`<키파일>`은 1단계에서 받은 개인키)
```bash
ssh -i <키파일.pem 또는 .key> ubuntu@<서버 공용 IP>
```

VM에서 Docker를 설치하고 작업 폴더를 만듭니다.
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER        # 적용하려면 한 번 로그아웃했다가 다시 접속
sudo mkdir -p /opt/atopass && sudo chown $USER /opt/atopass
```

메모리가 2GB인 서버에서는 순간적인 부족(PDF 생성 등)에 대비해 **스왑 2GB**를 추가합니다. (Oracle 12GB 서버는 생략해도 됩니다)
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

설정 파일(`.env`)을 만듭니다. 비밀값은 **이 파일에만** 적고, 저장소나 메신저에 올리지 않습니다.
```bash
cd /opt/atopass
nano .env
```
내용은 저장소의 `deploy/.env.example`을 보고 채웁니다.
```
DOMAIN=atopass.example.com
JWT_SECRET=<openssl rand -base64 48 로 만든 값>
DB_PASSWORD=<openssl rand -base64 24 로 만든 값>
MYSQL_ROOT_PASSWORD=<openssl rand -base64 24 로 만든 값>
ANTHROPIC_API_KEY=<챗봇 키>
```
```bash
chmod 600 .env
```

이미지가 비공개(private)라면 VM이 이미지를 받아올 수 있게 GitHub 로그인을 한 번 합니다.
GitHub → Settings → Developer settings → Personal access tokens(classic) → `read:packages` 권한으로 토큰을 만들고:
```bash
echo <토큰> | docker login ghcr.io -u <GitHub 아이디> --password-stdin
```
(패키지를 공개로 바꾸면 이 단계가 필요 없습니다. 이미지 안에는 비밀값이 들어 있지 않습니다.)

## 5. GitHub 설정 (한 번만)

**① 배포 전용 SSH 키 만들기** (내 PC에서. 비밀번호 없이 만든 새 키)
```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "atopass-deploy"
```
- `deploy_key.pub` 내용을 VM의 `~/.ssh/authorized_keys` 맨 아랫줄에 추가합니다.
- `deploy_key`(개인키)는 다음 단계에서 GitHub에만 넣고, 이후 PC에서 지웁니다.

**② 서버 지문(known_hosts) 얻기**
```bash
ssh-keyscan -t ed25519 <VM 공용 IP>
```
출력된 한 줄을 그대로 복사해 둡니다.

**③ 저장소 Secrets 등록:** Settings → Secrets and variables → Actions → New repository secret

| 이름 | 값 |
|---|---|
| `DEPLOY_HOST` | VM 공용 IP |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | `deploy_key` 파일 내용 전체 |
| `DEPLOY_KNOWN_HOSTS` | ②에서 복사한 한 줄 |
| `DEPLOY_DOMAIN` | `atopass.example.com` (https:// 없이) |

## 6. 첫 배포

1. `develop`의 내용을 `main`으로 PR을 올려 머지합니다. (또는 Actions 탭 → **Deploy** → Run workflow)
2. Actions 탭에서 진행을 봅니다. 테스트 → 이미지 빌드 → 서버 배포 → 배포 확인 순서이고 처음엔 10~20분 걸립니다.
3. 끝나면 `https://도메인`에 접속해 회원가입 → 로그인 → 챗봇 → 기록 저장 → PDF까지 확인합니다.

## 7. 이후 수정을 반영하는 방법

1. 평소처럼 기능 브랜치 → `develop` PR로 개발합니다.
2. 배포하고 싶을 때 `develop` → `main` PR을 만들어 머지합니다.
3. 자동으로 테스트, 빌드, 배포가 진행됩니다. (5~15분) 배포하는 동안 앱이 약 30초 멈췄다가 새 버전으로 돌아옵니다.
4. 테스트가 실패하면 배포가 중단되고 기존 버전이 그대로 유지됩니다.

**되돌리기:** 문제가 생기면 VM에서 이전 버전(커밋 해시)으로 되돌립니다.
```bash
cd /opt/atopass
APP_TAG=<이전 커밋 해시 전체> docker compose up -d
```
(GitHub의 Packages 화면이나 Actions 기록에서 이전 커밋 해시를 확인합니다.)

## 8. 백업과 복구

백업은 배포할 때마다 자동으로 한 번 하고, 매일 한 번 더 하도록 설정하는 것을 권장합니다.
```bash
crontab -e
# 아래 한 줄 추가 (매일 새벽 4시)
0 4 * * * /opt/atopass/backup.sh >> /opt/atopass/backup.log 2>&1
```
백업 파일은 `/opt/atopass/backups`에 14일 동안 보관됩니다. **이 폴더는 같은 VM 안에 있으니, 중요한 시점에는 내 PC로 내려받아 두세요.**
```bash
scp -i <키파일> ubuntu@<VM 공용 IP>:/opt/atopass/backups/db-*.sql.gz ./
```

DB 복구 예시:
```bash
cd /opt/atopass
gunzip -c backups/db-<시각>.sql.gz | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" atopass'
```

## 9. 자주 막히는 곳

| 증상 | 확인할 것 |
|---|---|
| 사이트가 열리지 않음 | 2단계 포트(콘솔 + VM 방화벽), 3단계 도메인 A 레코드 |
| HTTPS 인증서 오류 | 도메인이 VM IP를 가리키는지(`nslookup 도메인`), `docker compose logs caddy` |
| 배포 단계에서 SSH 실패 | `DEPLOY_*` Secrets 값, 공개키가 `authorized_keys`에 들어갔는지 |
| 이미지를 받아오지 못함 | 4단계 `docker login ghcr.io` |
| 앱이 계속 재시작됨 | `docker compose logs app` — 대부분 `.env` 값 누락(`JWT_SECRET`은 32바이트 이상) |
| 챗봇만 안 됨 | `ANTHROPIC_API_KEY`, Anthropic 크레딧 잔액 |
| 로그인이 계속 풀림 | 주소가 `https://`인지 확인 (쿠키가 HTTPS에서만 동작) |

로그 보기: `cd /opt/atopass && docker compose logs -f app`

## 10. 알아둘 점

- 무료 플랜/무료 VM은 **발표·시연용으로 충분**하지만 기간이 끝나면 유료로 바뀌거나 중단될 수 있습니다. 기간과 요금을 미리 확인해 두세요. (이 구성은 어느 서버에서든 그대로 쓸 수 있어 옮기기 쉽습니다.)
- AWS는 **서버를 중지/삭제할 때의 차이**를 알아 두세요. 중지(Stop)는 디스크 요금만 나가고 데이터는 유지됩니다. 삭제(Terminate)하면 디스크와 데이터가 사라집니다. 쓰지 않는 Elastic IP도 요금이 나가니 서버를 지울 때 함께 해제하세요.
- Oracle은 오래 사용하지 않는 무료 계정/자원을 회수할 수 있습니다. 주기적으로 접속하고 계속 켜 두세요.
- 보안 업데이트와 백업 보관은 직접 관리해야 합니다. (`sudo apt update && sudo apt upgrade`를 가끔 실행)
- 사진·PDF는 VM 디스크(Docker 볼륨 `app-data`)에 저장됩니다. VM을 지우면 함께 사라집니다.
