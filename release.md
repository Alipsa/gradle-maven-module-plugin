# gradle-maven-module-plugin release history

## v0.2.0 - In progress
- Changed maven central coordinates to se.alipsa.gradle.maven-module:se.alipsa.gradle.maven-module.gradle.plugin:version
- Publish the plugin to Maven Central on every release in additional to Plugin Portal.
- Added `dependsOnPublishedSubproject` and `dependsOnAllPublishedSubprojects()` to ensure Gradle subprojects' artifacts are published to the local Maven repository before Maven build phases run.
- Added exclusion support for `dependsOnAllPublishedSubprojects`: `exclude 'subproject'` for individual subprojects and `exclude group: 'parent'` for all subprojects under a parent path.


## v0.1.1 - 2026-03-18
- MavenModulePlugin.java:59 — Replaced deprecated project.container(Class, Factory) with project.getObjects().domainObjectContainer(Class, Factory) (the non-deprecated   
  Gradle 9.4 API)
- build.gradle — Added -Xlint:deprecation to compiler args so deprecation warnings surface explicitly in future builds
- declare CC compatibility for Gradle 9.4 and added a test for it in the compatibility matrix

## v0.1.0 - 2026-03-17
Initial release of the Gradle Maven Module Plugin. This plugin provides a convenient way to integrate Maven projects into a multi-project Gradle build, allowing you to manage and build Maven modules alongside standard Gradle projects. Key features include:
- Automatic task mapping from Gradle lifecycle tasks to corresponding Maven phases.
- Support for multiple Maven modules within a single Gradle subproject.
- Seamless integration with Gradle's dependency management and build lifecycle.