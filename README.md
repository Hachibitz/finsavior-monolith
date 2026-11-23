# FinSavior Monolith

Backend service for FinSavior, a personal finance application focused on bill tracking, subscriptions, goals, AI-assisted insights, and document/audio-based transaction entry.

This repository contains the Kotlin/Spring Boot monolith used by the mobile and web clients. It owns authentication, financial records, recurring bills, installments, AI integrations, subscription webhooks, caching, and operational APIs.

## Main Features

- JWT and Google authentication with refresh-token support.
- Bill, income, card, installment, and fixed bill management.
- Monthly dashboard and financial summary APIs.
- AI-powered advice, chat, document import, and audio transcription.
- Stripe subscription lifecycle handling through webhooks.
- Profile management, image upload validation, and account data cleanup.
- Caffeine-backed caching for frequently loaded financial views.

## Tech Stack

- Kotlin 1.9
- Spring Boot 3
- Spring Security
- Spring Data JPA / Hibernate
- MySQL
- Gradle
- LangChain4j and OpenAI APIs
- Stripe SDK
- Firebase Admin SDK

## Local Development

Prerequisites:

- JDK 21
- MySQL
- Gradle wrapper support
- Access to the FinSavior configuration values

Common commands:

```bash
./gradlew.bat compileKotlin
./gradlew.bat bootRun
```

## Release Notes

For this release, plan display prices are sourced live from Stripe and Google Play APIs (with a daily DB cache sync controlled by `finsavior.prices.sync-cron`, default `0 0 4 * * *`), and i18n/multi-currency features remain gated behind `finsavior.features.i18n-enabled` (default `false`). If Hibernate does not alter `plan_display_price` automatically, back up the database and run once before deploying:

```sql
scripts/release-2.2.0-plan-prices.sql
```

Savi receives the most recent eight conversation exchanges as separate user and
assistant messages. The optional chat request `date` is the selected month in
`yyyy-MM` format. Financial tool results are capped and indicate when rows were
omitted; they must not be treated as complete totals when truncated.

The `purchase_date` column stores the actual date independently of `bill_date`,
which is the billing month. Newly created installments keep the original
purchase date across all installments. Previously stored dates are not rewritten.
New recurring instances retain the exact purchase date only for the initial
month; subsequent instances do not invent a purchase date. Legacy recurring
dates may have been reconstructed from the billing month and need verification.
New chat history records identify coin-funded requests so they do not consume
the plan's chat quota; older records without a payment marker still count.

For release 2.3.0, check that `chat_message_history.paid_with_coins` exists
before deploying the new backend. If schema updates are not applied by
Hibernate, back up the database and run
`scripts/release-2.3.0-chat-usage.sql` once. Do not run it if the column
already exists. On rollback, keep the nullable column: dropping it would
permanently lose the payment markers recorded by the new version.

Database schema is currently managed by Hibernate updates. For release 2.0.0, legacy recurring bills can be linked to the new `fixed_bill` model by running:

```sql
scripts/migrate-legacy-recurrent-bills-to-fixed-bills.sql
```
