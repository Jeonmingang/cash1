// Minimal microservice stub using Node.js + Express
// This does NOT integrate with real PG or 문화상품권 providers.
// Replace with actual provider SDK/webhook logic.
const express = require('express');
const crypto = require('crypto');
const app = express();
app.use(express.urlencoded({extended:true}));
app.use(express.json());

const PORT = process.env.PORT || 3100;
const API_KEY = process.env.API_KEY || 'CHANGE_ME';
const HMAC_SECRET = process.env.HMAC_SECRET || 'CHANGE_ME';
const MANUAL_BANK = process.env.MANUAL_BANK || '토스뱅크';
const MANUAL_ACCOUNT = process.env.MANUAL_ACCOUNT || '100009904574';

function sign(body) {
  return crypto.createHmac('sha256', HMAC_SECRET).update(body).digest('hex');
}

function verify(req, res, next) {
  if (req.get('X-API-KEY') !== API_KEY) return res.status(401).send('bad_api_key');
  const raw = new URLSearchParams(req.body).toString();
  const s = sign(raw);
  if (req.get('X-Signature') !== s) return res.status(401).send('bad_signature');
  next();
}

// Create virtual account (stub)

app.post('/bank/create', verify, (req,res)=>{
  const {player, amount, serverId, mode} = req.body;
  // 주문ID는 매칭용으로만 사용(입금 메모에 이 값을 넣도록 안내)
  const orderId = 'ORD' + Date.now();
  const bank = MANUAL_BANK;           // <<< 고정: 사용자 개인계좌 은행명
  const account = MANUAL_ACCOUNT;   // <<< 고정: 사용자 개인계좌 번호
  const expires = '';               // 만료 없음

  // 플러그인이 이해하는 쿼리스트링 형식으로 응답
  const out = new URLSearchParams({orderId, bank, account, expires}).toString();
  res.type('text/plain').send(out);
});
// Gift verify (stub) - always VALID 10000원
app.post('/gift/verify', verify, (req,res)=>{
  const {player, vendor, code} = req.body;
  const out = new URLSearchParams({status:'VALID', value:'10000'}).toString();
  res.type('text/plain').send(out);
});

// Gift redeem (stub) - always REDEEMED 10000원
app.post('/gift/redeem', verify, (req,res)=>{
  const out = new URLSearchParams({status:'REDEEMED', value:'10000'}).toString();
  res.type('text/plain').send(out);
});

// Webhook forward helper (for PG -> plugin)
const FORWARD_URL = process.env.FORWARD_URL || 'http://127.0.0.1:27111/webhook/deposit';
const FORWARD_GIFT_URL = process.env.FORWARD_GIFT_URL || 'http://127.0.0.1:27111/webhook/gift';

function post(url, body){
  const fetch = require('node-fetch');
  const raw = new URLSearchParams(body).toString();
  return fetch(url, {
    method:'POST',
    headers: {'Content-Type':'application/x-www-form-urlencoded', 'X-Signature': sign(raw)},
    body: raw
  });
}

// Simulate a deposit webhook call to plugin
app.post('/simulate/deposit', (req,res)=>{
  const body = {event:'deposit.paid', orderId: req.body.orderId || ('ORD'+Date.now()), player:req.body.player||'Player', amount: req.body.amount || '5000'};
  post(FORWARD_URL, body).then(r=>r.text()).then(t=>res.send(t)).catch(e=>res.status(500).send(String(e)));
});

// Simulate a gift webhook call to plugin
app.post('/simulate/gift', (req,res)=>{
  const body = {event:'gift.redeemed', vendor:req.body.vendor||'cultureland', player:req.body.player||'Player', value: req.body.value || '10000'};
  post(FORWARD_GIFT_URL, body).then(r=>r.text()).then(t=>res.send(t)).catch(e=>res.status(500).send(String(e)));
});

app.listen(PORT, ()=>console.log('Stub listening '+PORT));
