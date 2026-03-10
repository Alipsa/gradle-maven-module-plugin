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
        // Create build.gradle applying the plugin
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }
                """);

        // Create settings.gradle
        writeFile("settings.gradle", """
                rootProject.name = 'test-maven-module'
                """);

        // Create pom.xml for a simple Maven project
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

        // Create a simple Java source file for Maven to compile
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
                result.task(":mavenClean").getOutcome());
    }

    @Test
    void assembleTaskInvokesMavenPackage() {
        BuildResult result = createRunner("assemble")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenPackage").getOutcome());

        // Verify the artifact was created
        File artifact = new File(projectDir, "target/test-maven-module-1.0.0.jar");
        assertTrue(artifact.exists(), "Maven should have produced the jar artifact");
    }

    @Test
    void checkTaskInvokesMavenVerify() {
        BuildResult result = createRunner("check")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenVerify").getOutcome());
    }

    @Test
    void buildTaskRunsAssembleAndCheck() {
        BuildResult result = createRunner("build")
                .build();

        assertNotNull(result.task(":mavenPackage"));
        assertNotNull(result.task(":mavenVerify"));
        assertEquals(TaskOutcome.SUCCESS,
                result.task(":build").getOutcome());
    }

    @Test
    void publishToMavenLocalInvokesMavenInstall() {
        BuildResult result = createRunner("publishToMavenLocal")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenInstall").getOutcome());
    }

    @Test
    void mavenTasksCanBeRunDirectly() {
        BuildResult result = createRunner("mavenCompile")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenCompile").getOutcome());
    }

    @Test
    void customProfilesAndPropertiesArePassedToMaven() throws IOException {
        // Modify build.gradle to include custom config
        writeFile("build.gradle", """
                plugins {
                    id 'se.alipsa.gradle.maven-module'
                }

                mavenModule {
                    systemProperties = ['skipTests': 'true']
                    args = ['--batch-mode']
                }
                """);

        BuildResult result = createRunner("assemble", "--info")
                .build();

        assertEquals(TaskOutcome.SUCCESS,
                result.task(":mavenPackage").getOutcome());
        assertTrue(result.getOutput().contains("-DskipTests=true"));
        assertTrue(result.getOutput().contains("--batch-mode"));
    }

    @Test
    void taskDependenciesAreCorrect() {
        BuildResult result = createRunner("tasks", "--all")
                .build();

        String output = result.getOutput();
        assertTrue(output.contains("mavenClean"));
        assertTrue(output.contains("mavenCompile"));
        assertTrue(output.contains("mavenTest"));
        assertTrue(output.contains("mavenPackage"));
        assertTrue(output.contains("mavenVerify"));
        assertTrue(output.contains("mavenInstall"));
        assertTrue(output.contains("mavenDeploy"));
    }

    private GradleRunner createRunner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();
    }

    private void writeFile(String path, String content) throws IOException {
        Path filePath = projectDir.toPath().resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
