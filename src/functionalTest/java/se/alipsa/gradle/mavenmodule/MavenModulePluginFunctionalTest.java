package se.alipsa.gradle.mavenmodule;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MavenModulePluginFunctionalTest {

    @TempDir
    File projectDir;

    @BeforeEach
    void setUp() throws IOException {
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    app {}
                }
                """);

        writeFile("settings.gradle", """
                rootProject.name = 'test-maven-module'
                """);

        writeFile("pom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-maven-module</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                </project>
                """);

        Path srcDir = projectDir.toPath()
                .resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("Hello.java"), """
                package com.example;

                public class Hello {
                    public String greet() {
                        return "Hello from Maven module!";
                    }
                }
                """);
    }

    @Test
    void cleanTaskInvokesMavenClean() {
        BuildResult result = createRunner("clean")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppClean").getOutcome());
    }

    @Test
    void assembleTaskInvokesMavenPackage() {
        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppPackage").getOutcome());

        File artifact = new File(projectDir, "target/test-maven-module-1.0.0.jar");
        assertTrue(artifact.exists(), "Maven should have produced the jar artifact");
    }

    @Test
    void checkTaskInvokesMavenVerify() {
        BuildResult result = createRunner("check")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppVerify").getOutcome());
    }

    @Test
    void buildTaskRunsAssembleAndCheck() {
        BuildResult result = createRunner("build")
                .build();

        assertNotNull(result.task(":mavenAppPackage"));
        assertNotNull(result.task(":mavenAppVerify"));
        assertEquals(TaskOutcome.SUCCESS,
                result.task(":build").getOutcome());
    }

    @Test
    void publishToMavenLocalInvokesMavenInstall() {
        BuildResult result = createRunner("publishToMavenLocal")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppInstall").getOutcome());
    }

    @Test
    void mavenTasksCanBeRunDirectly() {
        BuildResult result = createRunner("mavenAppCompile")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppCompile").getOutcome());
    }

    @Test
    void customProfilesAndPropertiesArePassedToMaven() throws IOException {
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }

                mavenModules {
                    app {
                        systemProperties = ['skipTests': 'true']
                        args = ['--batch-mode']
                    }
                }
                """);

        BuildResult result = createRunner("assemble", "--info")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenAppPackage").getOutcome());
        assertTrue(result.getOutput().contains("-DskipTests=true"));
        assertTrue(result.getOutput().contains("--batch-mode"));
    }

    @Test
    void taskDependenciesAreCorrect() {
        BuildResult result = createRunner("tasks", "--all")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("mavenAppClean"));
        assertTrue(output.contains("mavenAppCompile"));
        assertTrue(output.contains("mavenAppTest"));
        assertTrue(output.contains("mavenAppPackage"));
        assertTrue(output.contains("mavenAppVerify"));
        assertTrue(output.contains("mavenAppInstall"));
        assertTrue(output.contains("mavenAppDeploy"));
    }

    @Test
    void multipleModulesWithCustomPomFile() throws IOException {
        writeFile("bom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-bom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    bom {
                        pomFile = file('bom.xml')
                    }
                    app {
                        mustRunAfter 'bom'
                    }
                }
                """);

        // Verify both module tasks are registered
        BuildResult result = createRunner("tasks", "--all")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("mavenBomInstall"));
        assertTrue(output.contains("mavenBomPackage"));
        assertTrue(output.contains("mavenAppPackage"));
        assertTrue(output.contains("mavenAppInstall"));
    }

    @Test
    void multipleModulesRunInOrder() throws IOException {
        writeFile("bom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-bom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                mavenModules {
                    bom {
                        pomFile = file('bom.xml')
                    }
                    app {
                        mustRunAfter 'bom'
                    }
                }
                """);

        // Run install for both — bom should install before app starts
        BuildResult result = createRunner("mavenBomInstall", "mavenAppInstall")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenBomInstall").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":mavenAppInstall").getOutcome());

        // Verify ordering: bom install appears before app install in output
        String output = result.getOutput();
        int bomPos = output.indexOf("mavenBomInstall");
        int appPos = output.indexOf("mavenAppInstall");
        assertTrue(bomPos < appPos, "bom install should run before app install");
    }

    private GradleRunner createRunner(String... arguments) {
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();

        String testGradleVersion = System.getProperty("testGradleVersion", "");
        if (!testGradleVersion.isEmpty()) {
            runner.withGradleVersion(testGradleVersion);
        }

        return runner;
    }

    private void writeFile(String path, String content) throws IOException {
        Path filePath = projectDir.toPath().resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
