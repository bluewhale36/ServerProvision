# Repository Guidelines

## Project Structure & Module Organization

This is a Java 17 Spring Boot 4 application built with Gradle. Main code lives under `src/main/java/com/example/serverprovision` using feature-first packages: `management` for OS/board/BIOS resources, `maintenance` for reconciliation workflows, `provisioning` for user-facing provisioning, and `global` for shared jobs, marker infrastructure, exceptions, entities, and configuration. Thymeleaf views are in `src/main/resources/templates`, browser assets are in `src/main/resources/static`, and tests mirror production packages under `src/test/java`. `archive/legacy` is reference-only historical code.

## Current Implementation Status

Use `plan/` for intended slice design and `report/` for completed implementation summaries. The latest reports show G-MA3 and G-MK1 approved: BIOS bundle upload v3, global marker infrastructure, ISO sidecar markers, path reconciliation, async integrity verification, and background job chunk UI are implemented. The active/latest plan is `plan/26-04-25_23:56:59_MA1-async_plan.docx`, which covers ISO registration finalization through background jobs. Remaining management milestones are MA4 BMC and MA5 Driver, both expected to use Markable/marker patterns from BIOS and ISO.

## Build, Test, and Development Commands

- `./gradlew bootRun`: starts the local app. Set `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- `./gradlew test`: runs all JUnit Platform tests.
- `./gradlew test --tests com.example.serverprovision.management.os.controller.OSImageControllerUploadFlowTest`: runs one focused test class.
- `./gradlew build`: compiles, tests, and packages the app.

## Coding Style & Naming Conventions

Use 4-space Java indentation. Prefer Spring Boot conventions, records for simple DTOs, and Lombok where established. Package names are lowercase and feature-first. Use suffixes consistently: `*Controller`, `*RestController`, `*Service`, `*Repository`, `*Request`, `*Response`. Avoid primitive obsession for meaningful domain values such as paths, versions, hashes, marker signatures, MAC/IP addresses, and status values. Write Korean comments only when the reason is not obvious.

## Testing Guidelines

Tests use JUnit Platform with Spring Boot test slices and H2 where configured. Place tests in matching packages under `src/test/java`, name classes `*Test`, and use display names that state behavior and expected outcome. Add regressions for controller binding, upload flows, marker/hash behavior, background jobs, and reconciliation edge cases.

## Commit & Pull Request Guidelines

History uses Conventional Commit style with Korean descriptions, for example `feat(maintenance/os): ...`, `chore(ui): ...`, and `docs(claude): ...`. Keep commits scoped. PRs should include the problem, behavior changes, test commands, linked plan/report item, and screenshots for UI changes.

## Security & Configuration Tips

Do not commit secrets or production credentials. `application.properties` reads database, PXE, upload, marker, and reconciliation settings from environment variables. Override `PROVISION_MARKER_SECRET` outside development; the default is not production-safe.
