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

Database schema is currently managed by Hibernate updates. For release 2.0.0, legacy recurring bills can be linked to the new `fixed_bill` model by running:

```sql
scripts/migrate-legacy-recurrent-bills-to-fixed-bills.sql
```
