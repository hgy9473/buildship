/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.configuration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.core.internal.util.file.RelativePathUtils;

/**
 * Default implementation for {@link ConfigurationManager}.
 */
public class DefaultConfigurationManager implements ConfigurationManager {

    WorkspaceConfigurationPersistence workspaceConfigurationPersistence = new WorkspaceConfigurationPersistence();
    BuildConfigurationPersistence buildConfigurationPersistence = new BuildConfigurationPersistence();

    @Override
    public WorkspaceConfiguration loadWorkspaceConfiguration() {
        return this.workspaceConfigurationPersistence.readWorkspaceConfig();
    }

    @Override
    public void saveWorkspaceConfiguration(WorkspaceConfiguration config) {
        this.workspaceConfigurationPersistence.saveWorkspaceConfiguration(config);
    }

    @Override
    public BuildConfiguration loadBuildConfiguration(File rootDir) {
        Preconditions.checkNotNull(rootDir);
        Preconditions.checkArgument(rootDir.exists());
        Optional<IProject> projectCandidate = CorePlugin.workspaceOperations().findProjectByLocation(rootDir);
        if (projectCandidate.isPresent() && projectCandidate.get().isAccessible()) {
            IProject project = projectCandidate.get();
            try {
                return this.buildConfigurationPersistence.readBuildConfiguratonProperties(project);
            } catch (Exception e) {
                // when the project is being imported, the configuration file might not be visible from the
                // Eclipse resource API; in that case we fall back to raw IO operations
                // a similar approach is used in JDT core to load the .classpath file
                // see org.eclipse.jdt.internal.core.JavaProject.readFileEntriesWithException(Map)
                return this.buildConfigurationPersistence.readBuildConfiguratonProperties(project.getLocation().toFile());
            }
        } else {
            return this.buildConfigurationPersistence.readBuildConfiguratonProperties(rootDir);
        }
    }

    @Override
    public void saveBuildConfiguration(BuildConfiguration configuration) {
        File rootDir = getCanonicalFile(configuration.getRootProjectDirectory());
        Optional<IProject> rootProject = CorePlugin.workspaceOperations().findProjectByLocation(rootDir);
        if (rootProject.isPresent() && rootProject.get().isAccessible()) {
            this.buildConfigurationPersistence.saveBuildConfiguration(rootProject.get(), configuration);
        } else {
            this.buildConfigurationPersistence.saveBuildConfiguration(rootDir, configuration);
        }
    }

    private static File getCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    @Override
    public ProjectConfiguration createProjectConfiguration(BuildConfiguration configuration, File projectDir) {
        return new DefaultProjectConfiguration(projectDir, configuration);
    }

    @Override
    public ProjectConfiguration loadProjectConfiguration(IProject project) {
        String pathToRoot = this.buildConfigurationPersistence.readPathToRoot(project.getLocation().toFile());
        File rootDir = relativePathToProjectRoot(project.getLocation(), pathToRoot);
        BuildConfiguration buildConfig = loadBuildConfiguration(rootDir);
        return new DefaultProjectConfiguration(project.getLocation().toFile(), buildConfig);
    }

    @Override
    public ProjectConfiguration tryLoadProjectConfiguration(IProject project) {
        try {
            return loadProjectConfiguration(project);
        } catch(RuntimeException e) {
            CorePlugin.logger().debug("Cannot load configuration for project " + project.getName(), e);
            return null;
        }
    }

    private ProjectConfiguration loadProjectConfiguration(File projectDir) {
        String pathToRoot = this.buildConfigurationPersistence.readPathToRoot(projectDir);
        File rootDir = relativePathToProjectRoot(new Path(projectDir.getAbsolutePath()), pathToRoot);
        BuildConfiguration buildConfig = loadBuildConfiguration(rootDir);
        return new DefaultProjectConfiguration(canonicalize(projectDir), buildConfig);
    }

    @Override
    public void saveProjectConfiguration(ProjectConfiguration projectConfiguration) {
        BuildConfiguration buildConfiguration = projectConfiguration.getBuildConfiguration();
        saveBuildConfiguration(buildConfiguration);

        File projectDir = getCanonicalFile(projectConfiguration.getProjectDir());
        File rootDir = wgetCanonicalFile(buildConfiguration.getRootProjectDirectory());
        String pathToRoot = projectRootToRelativePath(projectDir, rootDir);

        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(projectDir);
        if (project.isPresent() && project.get().isAccessible()) {
            this.buildConfigurationPersistence.savePathToRoot(project.get(), pathToRoot);
        } else {
            this.buildConfigurationPersistence.savePathToRoot(projectDir, pathToRoot);
        }
    }

    @Override
    public void deleteProjectConfiguration(IProject project) {
        if (project.isAccessible()) {
            this.buildConfigurationPersistence.deletePathToRoot(project);
        } else {
            this.buildConfigurationPersistence.deletePathToRoot(project.getLocation().toFile());
        }
    }

    @Override
    public RunConfiguration loadRunConfiguration(ILaunchConfiguration launchConfiguration) {
        GradleRunConfigurationAttributes attributes = GradleRunConfigurationAttributes.from(launchConfiguration);
        ProjectConfiguration projectConfiguration;
        try {
            projectConfiguration = loadProjectConfiguration(attributes.getWorkingDir());
        } catch (Exception e) {
            CorePlugin.logger().debug("Can't load build config from " + attributes.getWorkingDir(), e);
            BuildConfiguration configuration = BuildConfiguration
                    .forRootProjectDirectory(attributes.getWorkingDir())
                    .gradleDistribution(attributes.getGradleDistribution())
                    .gradleUserHome(attributes.getGradleUserHome())
                    .overrideWorkspaceConfiguration(attributes.isOverrideBuildSettings())
                    .buildScansEnabled(attributes.isBuildScansEnabled())
                    .offlineMode(attributes.isOffline())
                    .build();
            // TODO (donat) we should use the facade here or inside DefaultRunConfiguration
            projectConfiguration = new DefaultProjectConfiguration(canonicalize(attributes.getWorkingDir()), configuration);
        }
        RunConfigurationProperties runConfigProperties = new RunConfigurationProperties(attributes.getTasks(),
                  attributes.getGradleDistribution(),
                  attributes.getGradleUserHome(),
                  attributes.getJavaHome(),
                  attributes.getJvmArguments(),
                  attributes.getArguments(),
                  attributes.isShowConsoleView(),
                  attributes.isShowExecutionView(),
                  attributes.isOverrideBuildSettings(),
                  attributes.isBuildScansEnabled(),
                  attributes.isOffline());
        return new DefaultRunConfiguration(projectConfiguration, runConfigProperties);
    }

    @Override
    public RunConfiguration createDefaultRunConfiguration(BuildConfiguration configuration) {
        return createRunConfiguration(configuration,
                Collections.<String>emptyList(),
                null,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                false,
                false,
                false,
                GradleDistribution.fromBuild(),
                null,
                false,
                false);
    }

    @Override
    public RunConfiguration createRunConfiguration(BuildConfiguration buildConfiguration, List<String> tasks, File javaHome, List<String> jvmArguments, List<String> arguments, boolean showConsoleView,
            boolean showExecutionsView, boolean overrideBuildSettings, GradleDistribution gradleDistribution, File gradleUserHome, boolean buildScansEnabled, boolean offlineMode) {
        ProjectConfiguration projectConfiguration = new DefaultProjectConfiguration(buildConfiguration.getRootProjectDirectory(), buildConfiguration);
        RunConfigurationProperties runConfig = new RunConfigurationProperties(tasks,
                gradleDistribution,
                gradleUserHome,
                javaHome,
                jvmArguments,
                arguments,
                showConsoleView,
                showExecutionsView,
                overrideBuildSettings,
                buildScansEnabled,
                offlineMode);
        return new DefaultRunConfiguration(projectConfiguration, runConfig);
    }

    private static File relativePathToProjectRoot(IPath projectPath, String path) {
        IPath pathToRoot = new Path(path);
        IPath absolutePathToRoot = pathToRoot.isAbsolute() ? pathToRoot : RelativePathUtils.getAbsolutePath(projectPath, pathToRoot);
        return canonicalize(absolutePathToRoot.toFile());
    }

    private static File canonicalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String projectRootToRelativePath(File projectDir, File rootDir) {
        IPath rootProjectPath = new Path(rootDir.getAbsoluteFile().getPath());
        IPath projectPath = new Path(projectDir.getAbsoluteFile().getPath());
        return RelativePathUtils.getRelativePath(projectPath, rootProjectPath).toPortableString();
    }
}
