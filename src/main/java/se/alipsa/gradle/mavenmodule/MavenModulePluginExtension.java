package se.alipsa.gradle.mavenmodule;

/**
 * Extension that holds configuration for the MavenModulePlugin.
 */
public class MavenModulePluginExtension {

    /** Path (relative to the Gradle project root) of the Maven project directory. */
    private String mavenProjectDir;

    public String getMavenProjectDir() {
        return mavenProjectDir;
    }

    public void setMavenProjectDir(String mavenProjectDir) {
        this.mavenProjectDir = mavenProjectDir;
    }
}
