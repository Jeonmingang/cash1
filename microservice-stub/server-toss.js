// Toss Payments microservice stub for Virtual Account
// Node.js + Express
// NOTE: Replace with your production-grade server (DB, retries, logging).
// Env: TOSS_SECRET_KEY, API_KEY, HMAC_SECRET, BANK_CODE(optional, default 'KB'), FORWARD_URL, FORWARD_GIFT_URL

const express = require('express');
const fetch = require('node-fetch');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(express.json());
app.use(express.urlencoded({extended:true}));

const PORT = process.env.PORT || 3100;
const API_KEY = process.env.API_KEY || 'CHANGE_ME';
const HMAC_SECRET = process.env.HMAC_SECRET || 'CHANGE_ME';
const TOSS_SECRET_KEY = process.env.TOSS_SECRET_KEY || 'test_sk_xxx';
const BANK_CODE = process.env.BANK_CODE || 'KB'; // 은행 코드 (docs.tosspayments.com 참고)
const FORWARD_URL = process.env.FORWARD_URL || 'http://127.0.0.1:27111/webhook/deposit';

const ORDERS_FILE = path.join(__dirname, 'orders.json');
let ORDERS = {};
try {
  if (fs.existsSync(ORDERS_FILE)) {
    ORDERS = JSON.parse(fs.readFileSync(ORDERS_FILE, 'utf8')||'{}');
  }
} catch(e){ ORDERS = {}; }
function saveOrders(){ fs.writeFileSync(ORDERS_FILE, JSON.stringify(ORDERS, null, 2)); }

function sign(body) {
  return crypto.createHmac('sha256', HMAC_SECRET).update(body).digest('hex');
}
function basicAuthHeader(secretKey){
  const token = Buffer.from(`${secretKey}:`).toString('base64');
  return `Basic ${token}`;
}
function queryString(obj){
  return new URLSearchParams(obj).toString();
}

// simple API-key + HMAC verify for plugin calls into this server
function verify(req,res,next){
  if (req.get('X-API-KEY') !== API_KEY) return res.status(401).send('bad_api_key');
  const raw = queryString(req.body);
  const sig = req.get('X-Signature');
  if (!sig || sig !== sign(raw)) return res.status(401).send('bad_signature');
  next();
}

// Create VA with Toss Payments
app.post('/bank/create', verify, async (req,res)=>{
  try{
    const { player, amount, serverId } = req.body;
    // Generate unique orderId and basic metadata
    const orderId = `ORD-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
    const orderName = `Donate ${player} @ ${serverId||'Server'}`;
    const body = {
      amount: parseInt(amount, 10),
      orderId,
      orderName,
      customerName: player,
      bank: BANK_CODE,
      validHours: 24
    };
    const r = await fetch('https://api.tosspayments.com/v1/virtual-accounts', {
      method: 'POST',
      headers: {
        'Authorization': basicAuthHeader(TOSS_SECRET_KEY),
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    });
    const data = await r.json();
    if (!r.ok){
      return res.status(500).type('text/plain').send(`error=${encodeURIComponent(data.message||'create_failed')}`);
    }
    // Persist mapping for webhook -> player
    ORDERS[orderId] = { player, amount: parseInt(amount,10), serverId, createdAt: new Date().toISOString() };
    saveOrders();

    const bank = (data.virtualAccount && (data.virtualAccount.bankCode||BANK_CODE)) || BANK_CODE;
    const account = (data.virtualAccount && data.virtualAccount.accountNumber) || '00000000000000';
    const expires = (data.virtualAccount && (data.virtualAccount.dueDate||'')) || '';

    const out = new URLSearchParams({orderId, bank, account, expires}).toString();
    res.type('text/plain').send(out);
  }catch(e){
    res.status(500).type('text/plain').send('error=' + encodeURIComponent(String(e)));
  }
});

// Toss webhooks (JSON): register this URL at Toss Dev Center
// We handle DEPOSIT_CALLBACK when status === 'DONE'
app.post('/pg/toss', async (req,res)=>{
  try{
    const { eventType, status, orderId } = req.body || {};
    // Optional: verify req.body.secret with Payment.secret (fetch from Toss API if needed)
    if (eventType === 'DEPOSIT_CALLBACK' && status === 'DONE' && orderId){
      const rec = ORDERS[orderId];
      const amount = (req.body.data && req.body.data.totalAmount) || (rec && rec.amount) || 0;
      if (!rec){ console.warn('order not found for webhook', orderId); }
      const body = {
        event: 'deposit.paid',
        orderId,
        player: rec ? rec.player : (req.body.customerName || 'Player'),
        amount: String(amount)
      };
      const raw = queryString(body);
      const forward = await fetch(FORWARD_URL, {
        method: 'POST',
        headers: {'Content-Type':'application/x-www-form-urlencoded', 'X-Signature': sign(raw)},
        body: raw
      });
      const text = await forward.text();
      console.log('Forwarded to plugin:', forward.status, text);
    }
    res.status(200).send('ok');
  }catch(e){
    console.error(e);
    res.status(500).send('error');
  }
});

// Utility endpoints to simulate webhook/deposit for local tests
app.post('/simulate/deposit', (req,res)=>{
  const body = {
    event:'deposit.paid',
    orderId: req.body.orderId || `ORD-${Date.now()}`,
    player: req.body.player || 'Player',
    amount: req.body.amount || '5000'
  };
  const raw = queryString(body);
  fetch(FORWARD_URL, {
    method:'POST',
    headers: {'Content-Type':'application/x-www-form-urlencoded','X-Signature': sign(raw)},
    body: raw
  }).then(r=>r.text()).then(t=>res.send(t)).catch(e=>res.status(500).send(String(e)));
});

app.listen(PORT, ()=>console.log('Toss microservice listening on '+PORT));
