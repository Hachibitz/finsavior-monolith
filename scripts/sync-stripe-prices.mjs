#!/usr/bin/env node
/**
 * Creates updated multi-currency recurring Prices in Stripe for the 6 FinSavior
 * subscription products and optionally archives older active prices on the same
 * product/interval.
 *
 * Usage:
 *   node scripts/sync-stripe-prices.mjs           # Dry-run (no writes)
 *   node scripts/sync-stripe-prices.mjs --apply   # Creates prices & archives old ones
 *
 * Optional env vars (will prompt interactively if omitted):
 *   STRIPE_SECRET_KEY
 *   STRIPE_BASIC_MONTHLY, STRIPE_BASIC_ANNUAL,
 *   STRIPE_PLUS_MONTHLY, STRIPE_PLUS_ANNUAL,
 *   STRIPE_PREMIUM_MONTHLY, STRIPE_PREMIUM_ANNUAL
 */

import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const APPLY = process.argv.includes('--apply');

// Zero-decimal currencies in Stripe (amount is already in whole units).
const ZERO_DECIMAL = new Set(['jpy', 'krw']);

const CATALOG = [
  {
    envKey: 'STRIPE_BASIC_MONTHLY',
    label: 'Basic Monthly',
    interval: 'month',
    brl: 7.9,
    currencies: { usd: 1.49, eur: 1.49, gbp: 1.29, mxn: 29, jpy: 220, krw: 2200, inr: 129, idr: 25000 },
  },
  {
    envKey: 'STRIPE_BASIC_ANNUAL',
    label: 'Basic Annual',
    interval: 'year',
    brl: 79.0,
    currencies: { usd: 14.99, eur: 14.99, gbp: 12.99, mxn: 299, jpy: 2200, krw: 22000, inr: 1299, idr: 250000 },
  },
  {
    envKey: 'STRIPE_PLUS_MONTHLY',
    label: 'Plus Monthly',
    interval: 'month',
    brl: 17.9,
    currencies: { usd: 3.49, eur: 3.49, gbp: 2.99, mxn: 69, jpy: 550, krw: 4900, inr: 299, idr: 55000 },
  },
  {
    envKey: 'STRIPE_PLUS_ANNUAL',
    label: 'Plus Annual',
    interval: 'year',
    brl: 179.9,
    currencies: { usd: 34.99, eur: 34.99, gbp: 29.99, mxn: 699, jpy: 5500, krw: 49000, inr: 2999, idr: 550000 },
  },
  {
    envKey: 'STRIPE_PREMIUM_MONTHLY',
    label: 'Premium Monthly',
    interval: 'month',
    brl: 34.9,
    currencies: { usd: 6.99, eur: 6.99, gbp: 5.99, mxn: 139, jpy: 1080, krw: 9900, inr: 599, idr: 109000 },
  },
  {
    envKey: 'STRIPE_PREMIUM_ANNUAL',
    label: 'Premium Annual',
    interval: 'year',
    brl: 349.0,
    currencies: { usd: 69.99, eur: 69.99, gbp: 59.99, mxn: 1399, jpy: 10800, krw: 99000, inr: 5999, idr: 1090000 },
  },
];

function toMinorUnits(amount, currency) {
  return ZERO_DECIMAL.has(currency.toLowerCase()) ? Math.round(amount) : Math.round(amount * 100);
}

async function stripeRequest(secretKey, path, method = 'GET', params = null) {
  const headers = { Authorization: `Bearer ${secretKey}` };
  let body;
  if (params) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    body = params.toString();
  }
  const res = await fetch(`https://api.stripe.com/v1${path}`, { method, headers, body });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`Stripe API ${res.status}: ${json?.error?.message || JSON.stringify(json)}`);
  }
  return json;
}

async function main() {
  const rl = readline.createInterface({ input, output });
  try {
    let secretKey = process.env.STRIPE_SECRET_KEY?.trim() || '';
    if (!secretKey) {
      secretKey = (await rl.question('Stripe Secret Key (sk_test_... ou sk_live_...): ')).trim();
    }
    if (!secretKey.startsWith('sk_')) {
      throw new Error('Chave Stripe inválida. Deve começar com sk_test_ ou sk_live_.');
    }
    const mode = secretKey.startsWith('sk_live_') ? 'LIVE (PRODUÇÃO)' : 'TEST (SANDBOX)';
    console.log(`\nModo detectado: ${mode} | Execução: ${APPLY ? 'APLICAR NA STRIPE' : 'DRY-RUN (sem alterações)'}\n`);

    const productIds = {};
    for (const item of CATALOG) {
      let pid = process.env[item.envKey]?.trim() || '';
      if (!pid) {
        pid = (await rl.question(`Product ID para ${item.label} (${item.envKey}, prod_...): `)).trim();
      }
      if (!pid.startsWith('prod_')) {
        throw new Error(`Product ID inválido para ${item.envKey}: "${pid}"`);
      }
      productIds[item.envKey] = pid;
    }

    if (APPLY) {
      const confirm = (await rl.question(`\nConfirmar criação de 6 preços em ${mode}? Digite "SIM" para continuar: `)).trim();
      if (confirm !== 'SIM') {
        console.log('Operação cancelada.');
        return;
      }
    }

    for (const item of CATALOG) {
      const productId = productIds[item.envKey];
      const existing = await stripeRequest(secretKey, `/prices?product=${encodeURIComponent(productId)}&active=true&limit=20`);
      const oldSameInterval = (existing.data || []).filter((p) => p.recurring?.interval === item.interval);

      console.log(`\n[${item.label}] (${productId}) — BRL ${item.brl.toFixed(2)} + ${Object.keys(item.currencies).length} moedas`);
      console.log(`  Preços ativos atuais no mesmo intervalo (${item.interval}): ${oldSameInterval.map((p) => p.id).join(', ') || 'nenhum'}`);

      if (!APPLY) continue;

      const form = new URLSearchParams();
      form.set('product', productId);
      form.set('currency', 'brl');
      form.set('unit_amount', String(toMinorUnits(item.brl, 'brl')));
      form.set('recurring[interval]', item.interval);
      for (const [cur, val] of Object.entries(item.currencies)) {
        form.set(`currency_options[${cur}][unit_amount]`, String(toMinorUnits(val, cur)));
      }

      const created = await stripeRequest(secretKey, '/prices', 'POST', form);
      console.log(`  ✔ Criado novo Price: ${created.id}`);

      for (const oldPrice of oldSameInterval) {
        const archiveForm = new URLSearchParams();
        archiveForm.set('active', 'false');
        await stripeRequest(secretKey, `/prices/${oldPrice.id}`, 'POST', archiveForm);
        console.log(`  ✔ Arquivado Price antigo: ${oldPrice.id}`);
      }
    }

    if (!APPLY) {
      console.log('\nDry-run concluído. Rode novamente com --apply para aplicar na Stripe.');
    } else {
      console.log('\nTodos os preços foram atualizados na Stripe!');
    }
  } finally {
    rl.close();
  }
}

main().catch((err) => {
  console.error('\nErro:', err.message);
  process.exit(1);
});
