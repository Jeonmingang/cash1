@echo off
REM ===== 개인계좌 수동정산 모드 실행 =====
set API_KEY=CHANGE_ME_RANDOM
set HMAC_SECRET=CHANGE_ME_RANDOM
set MANUAL_BANK=토스뱅크
set MANUAL_ACCOUNT=100009904574
node server.js
pause
