@echo off
REM ===== 개인계좌 수동정산 모드 실행 =====
REM 플러그인 config.yml과 동일하게 맞추세요
set API_KEY=CHANGE_ME_RANDOM
set HMAC_SECRET=CHANGE_ME_RANDOM

REM (선택) 문상 웹훅 포워드 URL - 바꾸지 마세요
set FORWARD_GIFT_URL=http://127.0.0.1:27111/webhook/gift

REM server.js는 /bank/create에서 개인계좌를 안내합니다.
set MANUAL_BANK=토스뱅크
set MANUAL_ACCOUNT=100009904574
node server.js
pause
