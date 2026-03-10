# gradle-maven-module-plugin

A Gradle plugin that allows a Maven project (with a `pom.xml`) to participate in a Gradle multi-project build. It maps Gradle lifecycle tasks to Maven phases so that standard Gradle commands work seamlessly with Maven subprojects.

## Use Case

In a multi-project Gradle build where some subprojects are Maven projects:

```
root/
├── settings.gradle
├── gradle-module/
│   └── build.gradle          (standard Gradle Java project)
└── maven-module/
    ├── build.gradle           (applies this plugin)
    └── pom.xml
```

## Setup

Apply the plugin in the Maven subproject's `build.gradle`:

```groovy
plugins {
    id 'se.alipsa.gradle.maven-module' version '0.1.0-SNAPSHOT'
}
```

Include it in your root `settings.gradle`:

```groovy
rootProject.name = 'my-project'
include 'gradle-module', 'maven-module'
```

## Task Mapping

| Gradle Task             | Maven Phase | Notes                            |
|-------------------------|-------------|----------------------------------|
| `clean`                 | `clean`     | Via `base` plugin's `clean` task |
| `assemble`              | `package`   | Produces the artifact            |
| `check`                 | `verify`    | Runs tests + integration tests   |
| `build`                 | (inherited) | `assemble` + `check` from `base` |
| `publishToMavenLocal`   | `install`   | Installs to local Maven repo    |
| `publish`               | `deploy`    | Deploys via Maven                |

Fine-grained `maven*` tasks are also registered for direct use:
`mavenCompile`, `mavenTest`, `mavenPackage`, `mavenVerify`, `mavenInstall`, `mavenDeploy`, `mavenClean`

## Configuration

```groovy
mavenModule {
    // Maven executable (auto-detects mvnw if not set)
    mavenExecutable = '/path/to/mvn'

    // Maven profiles to activate
    profiles = ['ci', 'integration']

    // System properties passed as -D flags
    systemProperties = ['skipTests': 'true', 'maven.javadoc.skip': 'true']

    // Additional CLI arguments
    args = ['--batch-mode', '-X']

    // Working directory (defaults to project.projectDir)
    workingDir = project.projectDir

    // Environment variables for the Maven process
    environment = ['JAVA_HOME': '/usr/lib/jvm/java-17']
}
```

## Maven Wrapper Support

The plugin automatically detects `mvnw` (or `mvnw.cmd` on Windows) in the project directory and parent directories. If found, it uses the wrapper instead of the system `mvn`. You can override this by setting `mavenExecutable`.

## POM Integration

The plugin parses `pom.xml` to:
- Set `project.group` and `project.version` from the POM's GAV coordinates
- Set `project.description` from the POM
- Expose the built artifact (e.g., `target/*.jar`) as a Gradle artifact

This means other Gradle modules can depend on the Maven module:

```groovy
// In another subproject's build.gradle
dependencies {
    implementation project(':maven-module')
}
```

## Building the Plugin

```bash
./gradlew build                  # Build and test
./gradlew publishToMavenLocal    # Publish to local Maven repo
```

## License

MIT License - see [LICENSE](LICENSE) for details.
