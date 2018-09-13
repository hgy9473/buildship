/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.configuration;

import java.io.File;
import java.util.Optional;

import com.google.common.base.Preconditions;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;

import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.GradlePluginsRuntimeException;

/**
 * Provides capability to read and save configuration properties on a target project.
 *
 * @author Donat Csikos
 */
final class BuildConfigurationPersistence {

    private static final String PREF_NODE = CorePlugin.PLUGIN_ID;

    private static final String PREF_KEY_CONNECTION_PROJECT_DIR = "connection.project.dir";
    private static final String PREF_KEY_CONNECTION_GRADLE_DISTRIBUTION = "connection.gradle.distribution";
    private static final String PREF_KEY_OVERRIDE_WORKSPACE_SETTINGS = "override.workspace.settings";
    private static final String PREF_KEY_GRADLE_USER_HOME = "gradle.user.home";
    private static final String PREF_KEY_BUILD_SCANS_ENABLED = "build.scans.enabled";
    private static final String PREF_KEY_OFFLINE_MODE = "offline.mode";
    private static final String PREF_KEY_AUTO_SYNC = "auto.sync";

    public BuildConfiguration readBuildConfiguratonProperties(IProject project) {
        Preconditions.checkNotNull(project);
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, PREF_NODE);
        return readPreferences(preferences, project.getLocation().toFile());
    }

    public BuildConfiguration readBuildConfiguratonProperties(File projectDir) {
        Preconditions.checkNotNull(projectDir);
        PreferenceStore preferences = PreferenceStore.forPreferenceFile(getProjectPrefsFile(projectDir, PREF_NODE));
        return readPreferences(preferences, projectDir);
    }

    public void saveBuildConfiguration(IProject project, BuildConfiguration properties) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(properties);
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, PREF_NODE);
        savePreferences(properties, preferences);
    }

    public void saveBuildConfiguration(File projectDir, BuildConfiguration properties) {
        Preconditions.checkNotNull(projectDir);
        Preconditions.checkNotNull(properties);
        PreferenceStore preferences = PreferenceStore.forPreferenceFile(getProjectPrefsFile(projectDir, PREF_NODE));
        savePreferences(properties, preferences);
    }

    public String readPathToRoot(IProject project) {
        Preconditions.checkNotNull(project);
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, PREF_NODE);
        String result = preferences.readString(PREF_KEY_CONNECTION_PROJECT_DIR, null);
        if (result == null) {
            throw new GradlePluginsRuntimeException("Can't read root project location for project " + project.getName());
        }
        return result;
    }

    public String readPathToRoot(File projectDir) {
        Preconditions.checkNotNull(projectDir);
        PreferenceStore preferences = PreferenceStore.forPreferenceFile(getProjectPrefsFile(projectDir, PREF_NODE));
        String result = preferences.readString(PREF_KEY_CONNECTION_PROJECT_DIR, null);
        if (result == null) {
            throw new GradlePluginsRuntimeException("Can't read root project location for project located at " + projectDir.getAbsolutePath());
        }
        return result;
    }

    public void savePathToRoot(IProject project, String pathToRoot) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(pathToRoot);
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, PREF_NODE);
        saveRootDirPreference(pathToRoot, preferences);

    }

    public void savePathToRoot(File projectDir, String pathToRoot) {
        Preconditions.checkNotNull(projectDir);
        Preconditions.checkNotNull(pathToRoot);
        PreferenceStore preferences = PreferenceStore.forPreferenceFile(getProjectPrefsFile(projectDir, PREF_NODE));
        saveRootDirPreference(pathToRoot, preferences);
    }

    public void deletePathToRoot(IProject project) {
        Preconditions.checkNotNull(project);
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, PREF_NODE);
        deleteRootDirPreference(preferences);
    }

    public void deletePathToRoot(File projectDir) {
        Preconditions.checkNotNull(projectDir);
        PreferenceStore preferences = PreferenceStore.forPreferenceFile(getProjectPrefsFile(projectDir, PREF_NODE));
        deleteRootDirPreference(preferences);
    }

    private static BuildConfiguration readPreferences(PreferenceStore preferences, File rootDir) {
        boolean overrideWorkspaceSettings = preferences.readBoolean(PREF_KEY_OVERRIDE_WORKSPACE_SETTINGS, false);

        String distributionString = preferences.readString(PREF_KEY_CONNECTION_GRADLE_DISTRIBUTION, null);
        GradleDistribution distribution;
        try {
            distribution = GradleDistribution.fromString(distributionString);
        } catch (RuntimeException ignore) {
            distribution = GradleDistribution.fromBuild();
        }

        String gradleUserHomeString = preferences.readString(PREF_KEY_GRADLE_USER_HOME, "");
        File gradleUserHome = gradleUserHomeString.isEmpty()
                ? null
                : new File(gradleUserHomeString);

        boolean buildScansEnabled = preferences.readBoolean(PREF_KEY_BUILD_SCANS_ENABLED, false);
        boolean offlineMode = preferences.readBoolean(PREF_KEY_OFFLINE_MODE, false);
        boolean autoSync = preferences.readBoolean(PREF_KEY_AUTO_SYNC, false);

        return BuildConfiguration.forRootProjectDirectory(rootDir)
                .gradleDistribution(distribution)
                .gradleUserHome(gradleUserHome)
                .overrideWorkspaceConfiguration(overrideWorkspaceSettings)
                .buildScansEnabled(buildScansEnabled)
                .offlineMode(offlineMode)
                .autoSync(autoSync)
                .build();
    }

    private static void savePreferences(BuildConfiguration properties, PreferenceStore preferences) {
        if (properties.isOverrideWorkspaceConfiguration()) {
            String gradleDistribution = properties.getGradleDistribution().toString();
            preferences.write(PREF_KEY_CONNECTION_GRADLE_DISTRIBUTION, gradleDistribution);
            preferences.write(PREF_KEY_GRADLE_USER_HOME, toPortableString(properties.getGradleUserHome()));
            preferences.writeBoolean(PREF_KEY_OVERRIDE_WORKSPACE_SETTINGS, properties.isOverrideWorkspaceConfiguration());
            preferences.writeBoolean(PREF_KEY_BUILD_SCANS_ENABLED, properties.isBuildScansEnabled());
            preferences.writeBoolean(PREF_KEY_OFFLINE_MODE, properties.isOfflineMode());
            preferences.writeBoolean(PREF_KEY_AUTO_SYNC, properties.isAutoSync());
        } else {
            preferences.delete(PREF_KEY_CONNECTION_GRADLE_DISTRIBUTION);
            preferences.delete(PREF_KEY_GRADLE_USER_HOME);
            preferences.delete(PREF_KEY_OVERRIDE_WORKSPACE_SETTINGS);
            preferences.delete(PREF_KEY_BUILD_SCANS_ENABLED);
            preferences.delete(PREF_KEY_OFFLINE_MODE);
            preferences.delete(PREF_KEY_AUTO_SYNC);
        }
        preferences.flush();
    }

    private static String toPortableString(Optional<File> file) {
        return file.map(f -> new Path(f.getPath()).toPortableString()).orElse("");
    }

    private static File getProjectPrefsFile(File projectDir, String node) {
        return new File(projectDir, ".settings/" + node + ".prefs");
    }

    private void saveRootDirPreference(String pathToRoot, PreferenceStore preferences) {
        preferences.write(PREF_KEY_CONNECTION_PROJECT_DIR, pathToRoot);
        preferences.flush();
    }

    private void deleteRootDirPreference(PreferenceStore preferences) {
        preferences.delete(PREF_KEY_CONNECTION_PROJECT_DIR);
        preferences.flush();
    }
}
