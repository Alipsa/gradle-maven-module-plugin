package se.alipsa.gradle.mavenmodule;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional test that verifies the plugin can be applied to a Gradle project.
 */
class MavenModulePluginFunctionalTest {

    @TempDir
    Path projectDir;

    private File getBuildFile() {
        return projectDir.resolve("build.gradle").toFile();
    }

    private File getSettingsFile() {
        return projectDir.resolve("settings.gradle").toFile();
    }

    @Test
    void canApplyPlugin() throws IOException {
        writeString(getSettingsFile(), "rootProject.name = 'test-project'");
        writeString(getBuildFile(),
                "plugins {\n" +
                "    id 'se.alipsa.maven-module'\n" +
                "}\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("help")
                .withPluginClasspath()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    @Test
    void canConfigureMavenProjectDir() throws IOException {
        writeString(getSettingsFile(), "rootProject.name = 'test-project'");
        writeString(getBuildFile(),
                "plugins {\n" +
                "    id 'se.alipsa.maven-module'\n" +
                "}\n" +
                "mavenModule {\n" +
                "    mavenProjectDir = '../some-maven-project'\n" +
                "}\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("help")
                .withPluginClasspath()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    private void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            writer.write(string);
        }
    }
}
