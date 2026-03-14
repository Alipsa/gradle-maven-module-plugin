package se.alipsa.gradle.mavenmodule;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gradle plugin that allows Maven projects (with pom.xml) to participate
 * in a Gradle multi-project build. Modules are configured via the
 * {@code mavenModules} container.
 */
public class MavenModulePlugin implements Plugin<Project> {

    private static final String[][] PHASES = {
        {"Clean", "clean"},
        {"Compile", "compile"},
        {"Test", "test"},
        {"Package", "package"},
        {"Verify", "verify"},
        {"Install", "install"},
        {"Deploy", "deploy"},
    };

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);

        // Apply POM metadata eagerly from the default pom.xml so that
        // group/version/description are available during configuration for
        // other plugins and build logic (e.g. publishing coordinates).
        // When the first module uses a non-default POM, metadata is applied
        // in afterEvaluate as a fallback once pomFile values are finalized.
        File defaultPom = project.file("pom.xml");
        boolean earlyMetadata = false;
        if (defaultPom.exists()) {
            PomInfo earlyInfo = parsePom(project, defaultPom);
            if (earlyInfo != null) {
                applyPomMetadata(project, earlyInfo);
                earlyMetadata = true;
            }
        }
        final boolean metadataAppliedEarly = earlyMetadata;

        NamedDomainObjectContainer<MavenModule> modules = project.container(
            MavenModule.class,
            name -> project.getObjects().newInstance(MavenModule.class, name)
        );
        project.getExtensions().add("mavenModules", modules);

        // Register tasks as modules are added to the container
        modules.all(module -> {
            module.getPomFile().convention(
                project.getLayout().getProjectDirectory().file("pom.xml")
            );
            // workingDir defaults to the POM file's parent directory
            module.getWorkingDir().convention(
                module.getPomFile().map(f -> f.getAsFile().getParentFile())
            );
            registerModuleTasks(project, module);
        });

        // After evaluation: wire publishing tasks, set up artifact integration,
        // wire ordering, and apply POM metadata as a fallback if the default
        // pom.xml was not present (e.g. when the first module uses a custom POM).
        project.afterEvaluate(p -> {
            // Create publishing lifecycle tasks if not already defined by another plugin.
            // Only wire dependencies when this plugin owns the tasks to avoid mutating
            // tasks registered by other plugins (e.g. maven-publish).
            boolean ownPublishToMavenLocal = findOrRegisterTask(p, "publishToMavenLocal",
                "Publishes Maven modules to the local Maven repository", "publishing");
            boolean ownPublish = findOrRegisterTask(p, "publish",
                "Publishes Maven modules using Maven deploy", "publishing");

            boolean metadataApplied = metadataAppliedEarly;
            for (MavenModule module : modules) {
                String cap = capitalize(module.getName());

                if (ownPublishToMavenLocal) {
                    p.getTasks().named("publishToMavenLocal",
                        t -> t.dependsOn(p.getTasks().named("maven" + cap + "Install")));
                }
                if (ownPublish) {
                    p.getTasks().named("publish",
                        t -> t.dependsOn(p.getTasks().named("maven" + cap + "Deploy")));
                }

                PomInfo pomInfo = parsePom(p, module.getPomFile().get().getAsFile());
                if (pomInfo != null) {
                    if (!metadataApplied) {
                        applyPomMetadata(p, pomInfo);
                        metadataApplied = true;
                    }
                    setupArtifactIntegration(p, module, pomInfo);
                }
            }
            wireOrdering(p, modules);
        });
    }

    private void registerModuleTasks(Project project, MavenModule module) {
        String cap = capitalize(module.getName());
        String prefix = "maven" + cap;
        Map<String, TaskProvider<MavenExecTask>> phaseTasks = new LinkedHashMap<>();

        for (String[] phaseEntry : PHASES) {
            String phaseLabel = phaseEntry[0];
            String phase = phaseEntry[1];
            String taskName = prefix + phaseLabel;

            TaskProvider<MavenExecTask> taskProvider = project.getTasks().register(
                taskName, MavenExecTask.class, task -> {
                    task.setDescription("Runs Maven " + phase + " phase for " + module.getName());
                    task.setGroup("maven");
                    task.getPhase().set(phase);
                    task.getPomFile().set(module.getPomFile());
                    task.getMavenExecutable().set(module.getMavenExecutable());
                    task.getProfiles().set(module.getProfiles());
                    task.getSystemProperties().set(module.getSystemProperties());
                    task.getArgs().set(module.getArgs());
                    task.getWorkingDir().set(module.getWorkingDir());
                    task.getEnvironment().set(module.getEnvironment());
                }
            );
            phaseTasks.put(phase, taskProvider);
        }

        // Intra-module task ordering
        phaseTasks.get("test").configure(t -> t.mustRunAfter(phaseTasks.get("compile")));
        phaseTasks.get("package").configure(t -> t.mustRunAfter(phaseTasks.get("test")));
        phaseTasks.get("verify").configure(t -> t.mustRunAfter(phaseTasks.get("package")));
        phaseTasks.get("install").configure(t -> t.mustRunAfter(phaseTasks.get("verify")));
        phaseTasks.get("deploy").configure(t -> t.mustRunAfter(phaseTasks.get("install")));

        // Wire to lifecycle tasks (from BasePlugin)
        project.getTasks().named("clean", t -> t.dependsOn(phaseTasks.get("clean")));
        project.getTasks().named("assemble", t -> t.dependsOn(phaseTasks.get("package")));
        project.getTasks().named("check", t -> t.dependsOn(phaseTasks.get("verify")));
        // publishToMavenLocal and publish are wired in afterEvaluate to avoid conflicts
    }

    private void wireOrdering(Project project, NamedDomainObjectContainer<MavenModule> modules) {
        for (MavenModule module : modules) {
            for (String afterName : module.getMustRunAfterModules()) {
                MavenModule afterModule = modules.findByName(afterName);
                if (afterModule == null) {
                    project.getLogger().warn(
                        "mavenModules: '{}' declares mustRunAfter '{}' which does not exist",
                        module.getName(), afterName);
                    continue;
                }

                String modulePrefix = "maven" + capitalize(module.getName());
                String afterPrefix = "maven" + capitalize(afterModule.getName());

                // All tasks of this module must run after all tasks of the other module
                for (String[] phaseEntry : PHASES) {
                    project.getTasks().named(modulePrefix + phaseEntry[0], task -> {
                        for (String[] afterPhaseEntry : PHASES) {
                            task.mustRunAfter(project.getTasks().named(afterPrefix + afterPhaseEntry[0]));
                        }
                    });
                }
            }
        }
    }

    private void applyPomMetadata(Project project, PomInfo pomInfo) {
        if (pomInfo.groupId != null && project.getGroup().toString().isEmpty()) {
            project.setGroup(pomInfo.groupId);
        }
        if (pomInfo.version != null && Project.DEFAULT_VERSION.equals(project.getVersion())) {
            project.setVersion(pomInfo.version);
        }
        if (pomInfo.description != null && project.getDescription() == null) {
            project.setDescription(pomInfo.description);
        }
    }

    private void setupArtifactIntegration(Project project, MavenModule module, PomInfo pomInfo) {
        String packaging = pomInfo.packaging != null ? pomInfo.packaging : "jar";
        if ("pom".equals(packaging)) {
            return;
        }

        String artifactId = pomInfo.artifactId;
        String version = pomInfo.version;
        if (artifactId == null || version == null) {
            return;
        }

        project.getConfigurations().maybeCreate("default");

        String cap = capitalize(module.getName());
        String artifactFileName = artifactId + "-" + version + "." + packaging;
        // Maven writes to target/ relative to the POM file's directory, not workingDir
        File pomDir = module.getPomFile().get().getAsFile().getParentFile();
        File artifactFile = new File(pomDir, "target/" + artifactFileName);

        project.getArtifacts().add("default", artifactFile, artifact -> {
            artifact.setType(packaging);
            artifact.builtBy(project.getTasks().named("maven" + cap + "Package"));
        });
    }

    static PomInfo parsePom(Project project, File pomFile) {
        if (!pomFile.exists()) {
            project.getLogger().warn("POM file not found: {}", pomFile);
            return null;
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            Document doc = dbf.newDocumentBuilder().parse(pomFile);
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
            project.getLogger().error("Failed to parse {}: {}", pomFile.getName(), e.getMessage(), e);
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

    private static boolean findOrRegisterTask(Project project, String name,
                                               String description, String group) {
        if (project.getTasks().getNames().contains(name)) {
            return false;
        }
        project.getTasks().register(name, task -> {
            task.setDescription(description);
            task.setGroup(group);
        });
        return true;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static class PomInfo {
        String groupId;
        String artifactId;
        String version;
        String packaging;
        String description;
    }
}
