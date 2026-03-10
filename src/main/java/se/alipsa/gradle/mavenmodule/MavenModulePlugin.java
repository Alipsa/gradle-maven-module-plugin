package se.alipsa.gradle.mavenmodule;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

/**
 * Gradle plugin that allows a Maven project (with pom.xml) to participate
 * in a Gradle multi-project build.
 */
public class MavenModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Apply the base plugin to get clean, assemble, check, build lifecycle tasks
        project.getPluginManager().apply(BasePlugin.class);

        // Create the extension
        MavenModuleExtension extension = project.getExtensions()
                .create("mavenModule", MavenModuleExtension.class);

        // Set defaults
        extension.getWorkingDir().convention(project.getProjectDir());

        // Parse pom.xml for project metadata
        PomInfo pomInfo = parsePom(project);
        if (pomInfo != null) {
            if (pomInfo.groupId != null) {
                project.setGroup(pomInfo.groupId);
            }
            if (pomInfo.version != null) {
                project.setVersion(pomInfo.version);
            }
            if (pomInfo.description != null) {
                project.setDescription(pomInfo.description);
            }
        }

        // Register fine-grained maven tasks
        TaskProvider<MavenExecTask> mavenClean = registerMavenTask(project, "mavenClean", "clean", extension, "Runs Maven clean phase");
        TaskProvider<MavenExecTask> mavenCompile = registerMavenTask(project, "mavenCompile", "compile", extension, "Runs Maven compile phase");
        TaskProvider<MavenExecTask> mavenTest = registerMavenTask(project, "mavenTest", "test", extension, "Runs Maven test phase");
        TaskProvider<MavenExecTask> mavenPackage = registerMavenTask(project, "mavenPackage", "package", extension, "Runs Maven package phase");
        TaskProvider<MavenExecTask> mavenVerify = registerMavenTask(project, "mavenVerify", "verify", extension, "Runs Maven verify phase");
        TaskProvider<MavenExecTask> mavenInstall = registerMavenTask(project, "mavenInstall", "install", extension, "Runs Maven install phase");
        TaskProvider<MavenExecTask> mavenDeploy = registerMavenTask(project, "mavenDeploy", "deploy", extension, "Runs Maven deploy phase");

        // Set up task dependency chain
        mavenTest.configure(t -> t.mustRunAfter(mavenCompile));
        mavenPackage.configure(t -> t.mustRunAfter(mavenTest));
        mavenVerify.configure(t -> t.mustRunAfter(mavenPackage));
        mavenInstall.configure(t -> t.mustRunAfter(mavenVerify));
        mavenDeploy.configure(t -> t.mustRunAfter(mavenInstall));

        // Wire lifecycle tasks
        project.getTasks().named("clean", task -> task.dependsOn(mavenClean));
        project.getTasks().named("assemble", task -> task.dependsOn(mavenPackage));
        project.getTasks().named("check", task -> task.dependsOn(mavenVerify));

        // Register publishToMavenLocal and publish tasks
        project.getTasks().register("publishToMavenLocal", task -> {
            task.setDescription("Publishes Maven module to the local Maven repository");
            task.setGroup("publishing");
            task.dependsOn(mavenInstall);
        });

        project.getTasks().register("publish", task -> {
            task.setDescription("Publishes Maven module using Maven deploy");
            task.setGroup("publishing");
            task.dependsOn(mavenDeploy);
        });

        // Artifact integration: expose the Maven-built artifact to Gradle
        if (pomInfo != null) {
            setupArtifactIntegration(project, pomInfo, mavenPackage);
        }
    }

    private TaskProvider<MavenExecTask> registerMavenTask(
            Project project, String taskName, String phase,
            MavenModuleExtension extension, String description) {

        return project.getTasks().register(taskName, MavenExecTask.class, task -> {
            task.setDescription(description);
            task.setGroup("maven");
            task.getPhase().set(phase);
            task.getMavenExecutable().set(extension.getMavenExecutable());
            task.getProfiles().set(extension.getProfiles());
            task.getSystemProperties().set(extension.getSystemProperties());
            task.getArgs().set(extension.getArgs());
            task.getWorkingDir().set(extension.getWorkingDir());
            task.getEnvironment().set(extension.getEnvironment());
        });
    }

    private void setupArtifactIntegration(Project project, PomInfo pomInfo,
                                          TaskProvider<MavenExecTask> mavenPackage) {
        String packaging = pomInfo.packaging != null ? pomInfo.packaging : "jar";
        String artifactId = pomInfo.artifactId;
        String version = pomInfo.version;

        if (artifactId == null || version == null) {
            return;
        }

        Configuration defaultConfig = project.getConfigurations().maybeCreate("default");

        String artifactFileName = artifactId + "-" + version + "." + packaging;
        File artifactFile = new File(project.getProjectDir(), "target/" + artifactFileName);

        project.getArtifacts().add("default", artifactFile, artifact -> {
            artifact.setType(packaging);
            artifact.builtBy(mavenPackage);
        });
    }

    static PomInfo parsePom(Project project) {
        File pomFile = new File(project.getProjectDir(), "pom.xml");
        if (!pomFile.exists()) {
            project.getLogger().warn("No pom.xml found in {}", project.getProjectDir());
            return null;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(pomFile);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            PomInfo info = new PomInfo();
            info.groupId = getDirectChildText(root, "groupId");
            info.artifactId = getDirectChildText(root, "artifactId");
            info.version = getDirectChildText(root, "version");
            info.packaging = getDirectChildText(root, "packaging");
            info.description = getDirectChildText(root, "description");

            // If groupId or version are not set directly, try to inherit from parent
            if (info.groupId == null || info.version == null) {
                NodeList parentNodes = root.getElementsByTagName("parent");
                if (parentNodes.getLength() > 0) {
                    Element parent = (Element) parentNodes.item(0);
                    if (info.groupId == null) {
                        info.groupId = getDirectChildText(parent, "groupId");
                    }
                    if (info.version == null) {
                        info.version = getDirectChildText(parent, "version");
                    }
                }
            }

            return info;
        } catch (Exception e) {
            project.getLogger().error("Failed to parse pom.xml: {}", e.getMessage());
            return null;
        }
    }

    private static String getDirectChildText(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                Element child = (Element) nodes.item(i);
                if (child.getTagName().equals(tagName)) {
                    String text = child.getTextContent().trim();
                    return text.isEmpty() ? null : text;
                }
            }
        }
        return null;
    }

    static class PomInfo {
        String groupId;
        String artifactId;
        String version;
        String packaging;
        String description;
    }
}
