package de.kallifabio.cloud.setup;

import de.kallifabio.cloud.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public final class SetupValidator {

    private SetupValidator() {
    }

    public static Map<String, Object> buildReport(ConfigManager configManager, boolean createMissingTemplateDirs) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> groups = new ArrayList<>();

        int okGroups = 0;
        int warnGroups = 0;
        int totalIssues = 0;

        List<String> groupNames = new ArrayList<>(configManager.getAllServerGroups());
        groupNames.sort(String::compareToIgnoreCase);
        for (String groupName : groupNames) {
            Map<String, Object> groupReport = inspectGroup(configManager, groupName, createMissingTemplateDirs);
            groups.add(groupReport);

            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) groupReport.get("issues");
            if (issues.isEmpty()) {
                okGroups++;
            } else {
                warnGroups++;
                totalIssues += issues.size();
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groupsTotal", groupNames.size());
        summary.put("groupsOk", okGroups);
        summary.put("groupsWithIssues", warnGroups);
        summary.put("issuesTotal", totalIssues);

        report.put("timestamp", System.currentTimeMillis());
        report.put("fixApplied", createMissingTemplateDirs);
        report.put("summary", summary);
        report.put("groups", groups);
        return report;
    }

    private static Map<String, Object> inspectGroup(ConfigManager configManager, String groupName, boolean createMissingTemplateDirs) {
        Map<String, Object> group = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        boolean isProxy = isProxyGroup(groupName);
        boolean dynamic = configManager.isDynamicGroup(groupName);
        String expectedJar = isProxy ? "bungeecord.jar" : "spigot.jar";

        File templateDir = resolveGroupDirectory("./templates", groupName);
        File templateTestDir = resolveGroupDirectory("./templates_test", groupName);
        File backupTemplateDir = resolveGroupDirectory("./templates_backup", groupName);
        File jarsGroupDir = resolveGroupDirectory("./jars", groupName);
        File serverGroupDir = new File("./servers/" + groupName);

        boolean templateExists = templateDir.exists();
        boolean templateTestExists = templateTestDir.exists();
        boolean backupTemplateExists = backupTemplateDir.exists();

        if (!templateExists && !backupTemplateExists) {
            issues.add("Kein Template vorhanden (weder templates/ noch templates_backup/).");
            if (createMissingTemplateDirs) {
                try {
                    Files.createDirectories(templateDir.toPath());
                    notes.add("Template-Verzeichnis erstellt: " + templateDir.getAbsolutePath());
                    templateExists = true;
                } catch (IOException e) {
                    issues.add("Template-Verzeichnis konnte nicht erstellt werden: " + e.getMessage());
                }
            }
        }

        List<String> jarAliases = getJarCandidateNames(expectedJar);
        List<File> searchDirectories = getJarSearchDirectories(groupName);
        File firstFoundJar = null;
        for (File directory : searchDirectories) {
            firstFoundJar = findMatchingJarInDirectory(directory, jarAliases);
            if (firstFoundJar != null) {
                break;
            }
        }
        List<File> candidates = buildJarCandidates(searchDirectories, jarAliases);
        if (firstFoundJar == null) {
            issues.add("Keine passende JAR gefunden (" + String.join(", ", jarAliases) + ", case-insensitive, inkl. <name>-*.jar).");
        }

        if (dynamic && !templateExists && !templateTestExists && !backupTemplateExists) {
            issues.add("Gruppe ist Dynamic=true, aber es gibt keine Template-Quelle.");
        }

        group.put("groupName", groupName);
        group.put("dynamic", dynamic);
        group.put("proxyGroup", isProxy);
        group.put("expectedJar", expectedJar);
        group.put("jarAliases", jarAliases);
        group.put("jarFound", firstFoundJar != null);
        group.put("jarPath", firstFoundJar == null ? null : firstFoundJar.getAbsolutePath());
        group.put("templateDir", templateDir.getAbsolutePath());
        group.put("templateExists", templateExists);
        group.put("templateTestDir", templateTestDir.getAbsolutePath());
        group.put("templateTestExists", templateTestExists);
        group.put("backupTemplateDir", backupTemplateDir.getAbsolutePath());
        group.put("backupTemplateExists", backupTemplateExists);
        group.put("serversDir", serverGroupDir.getAbsolutePath());
        group.put("serversDirExists", serverGroupDir.exists());
        group.put("checkedJarPaths", candidates.stream().map(File::getAbsolutePath).toList());
        group.put("notes", notes);
        group.put("issues", issues);
        group.put("healthy", issues.isEmpty());
        return group;
    }

    private static boolean isProxyGroup(String groupName) {
        String g = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
        return g.contains("proxy") || g.contains("bungee") || g.contains("waterfall") || g.contains("velocity");
    }

    private static List<String> getJarCandidateNames(String expectedJarName) {
        if ("bungeecord.jar".equalsIgnoreCase(expectedJarName)) {
            return List.of("bungeecord.jar", "waterfall.jar", "velocity.jar", "proxy.jar");
        }
        return List.of("spigot.jar", "paper.jar", "purpur.jar", "server.jar");
    }

    private static List<File> buildJarCandidates(List<File> searchDirectories, List<String> aliasNames) {
        List<File> candidates = new ArrayList<>();
        for (File baseDir : searchDirectories) {
            for (String alias : aliasNames) {
                candidates.add(new File(baseDir, alias));
                candidates.add(new File(baseDir, alias.replace(".jar", "-*.jar")));
            }
        }
        return candidates;
    }

    private static List<File> getJarSearchDirectories(String groupName) {
        File templateDir = resolveGroupDirectory("./templates", groupName);
        File templateTestDir = resolveGroupDirectory("./templates_test", groupName);
        File templateBackupDir = resolveGroupDirectory("./templates_backup", groupName);
        File jarsGroupDir = resolveGroupDirectory("./jars", groupName);
        File serverGroupDir = new File("./servers/" + groupName);
        return List.of(
                serverGroupDir,
                templateDir,
                templateTestDir,
                templateBackupDir,
                jarsGroupDir,
                new File("./jars"),
                new File(".")
        );
    }

    private static File findMatchingJarInDirectory(File directory, List<String> aliasNames) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }
        File[] files = directory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return null;
        }

        for (String alias : aliasNames) {
            String aliasLower = alias.toLowerCase(Locale.ROOT);
            String baseLower = aliasLower.endsWith(".jar")
                    ? aliasLower.substring(0, aliasLower.length() - 4)
                    : aliasLower;
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar")) {
                    continue;
                }
                if (name.equals(aliasLower)) {
                    return file;
                }
                if (name.startsWith(baseLower + "-")) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File resolveGroupDirectory(String baseDir, String groupName) {
        File base = new File(baseDir);
        File exact = new File(base, groupName);
        if (exact.exists()) {
            return exact;
        }
        File[] children = base.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(groupName)) {
                    return child;
                }
            }
        }
        return exact;
    }
}
