# UltimateDonateAutomation v1.0.0

Minecraft 1.16.5 / Java 8 / CatServer 호환. 후원 가상계좌 + 문화상품권 자동화 스켈레톤 플러그인.

## 핵심 기능
- `/donate bank <금액>`: 마이크로서비스를 통해 가상계좌 발급 → 입금 웹훅 수신 시 자동 보상
- `/donate code <코드> [vendor]`: 문화상품권 코드 저장/검증/자동등록(AUTO_REDEEM 모드)
- 웹훅 내장 서버 (127.0.0.1:27111) + HMAC-SHA256 서명 검증
- Vault 미탑재 시 콘솔 명령어 기반 보상 실행 (NPE 가드)

## 빠른 테스트
1) `plugins/UltimateDonate/config.yml`에서 `microservice.baseUrl` 및 `security.hmacSecret` 설정
2) `/microservice-stub`에서 `npm i express node-fetch` 후 `API_KEY/HMAC_SECRET` 환경변수 설정하고 `node server.js`
3) 게임 내 `/donate bank 5000` 실행 → 서버 콘솔에서 `curl -XPOST http://localhost:3100/simulate/deposit -d "orderId=...&player=닉네임&amount=5000"` 호출
4) 보상 지급 로그 및 메시지 확인

## 실제 PG/문화상품권 연동 가이드
- PG: Iamport(아임포트), Toss Payments 등의 **가상계좌 + 웹훅** 사용을 권장
- 문화상품권: Cultureland/해피머니/북앤라이프의 공식/제휴 API가 아닌 **스크래핑 사용 금지**. 합법적 제휴 API 사용 또는 STORE_ONLY 모드 권장.

## 빌드 방법
- Maven (maven-shade 사용 안함)
- `mvn -q -e -DskipTests package`
- 산출물: `target/ultimate-donate-1.0.0.jar`

## 구성 요소
- `src/main/java/com/minkang/ultimate/**` (전체 소스)
- `src/main/resources/plugin.yml, config.yml`
- `microservice-stub/server.js` (테스트용)

