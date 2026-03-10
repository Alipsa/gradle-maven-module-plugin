# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Gradle plugin (`se.alipsa.gradle.maven-module`) that allows Maven projects with `pom.xml` to participate in Gradle multi-project builds. It maps Gradle lifecycle tasks to Maven phases (e.g., `assemble`→`package`, `check`→`verify`).

## Build Commands

```bash
./gradlew build                  # Build, test (unit + functional), and validate
./gradlew test                   # Unit tests only
./gradlew functionalTest         # Functional tests only (uses GradleRunner/TestKit)
./gradlew publishToMavenLocal    # Publish plugin to local Maven repo

# Run a single test
./gradlew test --tests MavenModulePluginTest.pluginAppliesSuccessfully
./gradlew functionalTest --tests MavenModulePluginFunctionalTest.cleanTaskInvokesMavenClean
```

## Architecture

Three classes in `se.alipsa.gradle.mavenmodule`:

- **MavenModulePlugin** — Plugin entry point. Applies `BasePlugin`, creates the `mavenModule` extension, registers all `maven*` tasks, wires them to Gradle lifecycle tasks, parses `pom.xml` for GAV metadata, and exposes Maven-built artifacts to Gradle's dependency system.

- **MavenExecTask** — Custom task that invokes a Maven phase via CLI. Uses `ExecOperations` for process execution. Auto-detects `mvnw` in project/parent directories, falls back to system `mvn`. Annotated `@DisableCachingByDefault` since Maven execution is not cacheable.

- **MavenModuleExtension** — Abstract DSL extension class using Gradle's `Property`/`ListProperty`/`MapProperty` API for lazy configuration (`mavenExecutable`, `profiles`, `systemProperties`, `args`, `workingDir`, `environment`).

## Testing

- **Unit tests** (`src/test/`) use `ProjectBuilder` for lightweight in-memory testing of plugin configuration, task registration, POM parsing, and command-line building. Requires `gradleApi()` as explicit `testImplementation` dependency (Gradle 9.4+ moved it to `compileOnlyApi`).

- **Functional tests** (`src/functionalTest/`) use `GradleRunner` (TestKit) to execute real builds in temp directories with actual Maven invocations. Registered via `gradlePlugin.testSourceSets()`.

## Key Gradle 9.4 Considerations

- `java-gradle-plugin` adds `gradleApi()` to `compileOnlyApi` (not `api`), so tests need explicit `testImplementation gradleApi()`.
- Published plugin tasks must have `@DisableCachingByDefault` or `@CacheableTask` annotation.
