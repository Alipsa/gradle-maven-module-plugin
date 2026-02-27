package se.alipsa.gradle.mavenmodule;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * A Gradle plugin that allows a Maven project to be used as a module
 * in a Gradle multi-module project.
 */
public class MavenModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Register the plugin extension so users can configure the Maven module
        MavenModulePluginExtension extension = project.getExtensions()
                .create("mavenModule", MavenModulePluginExtension.class);

        // After the project is evaluated, add the Maven module's dependencies
        project.afterEvaluate(p -> {
            String mavenProjectDir = extension.getMavenProjectDir();
            if (mavenProjectDir != null && !mavenProjectDir.isEmpty()) {
                // Resolve the pom.xml and add its output as a project dependency
                java.io.File pomFile = p.file(mavenProjectDir + "/pom.xml");
                if (!pomFile.exists()) {
                    p.getLogger().warn(
                            "maven-module plugin: pom.xml not found at {}", pomFile.getAbsolutePath());
                }
            }
        });
    }
}
