package se.alipsa.gradle.mavenmodule;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MavenModulePluginExtensionTest {

    @Test
    void testDefaultMavenProjectDirIsNull() {
        MavenModulePluginExtension ext = new MavenModulePluginExtension();
        assertNull(ext.getMavenProjectDir());
    }

    @Test
    void testSetAndGetMavenProjectDir() {
        MavenModulePluginExtension ext = new MavenModulePluginExtension();
        ext.setMavenProjectDir("../my-maven-module");
        assertEquals("../my-maven-module", ext.getMavenProjectDir());
    }
}
