/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.internal.configuration

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.configuration.BuildConfiguration
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification;

@SuppressWarnings("GroovyAccessibility")
class BuildConfigurationFacadeTest extends ProjectSynchronizationSpecification {

    def "new build configuration can inherit workspace settings"(GradleDistribution distribution, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        setup:
        File projectDir = dir('project-dir')
        File workspaceGradleUserHome = dir('workspace-gradle-user-home').canonicalFile
        WorkspaceConfiguration orignalConfiguration = configurationManager.loadWorkspaceConfiguration()

        when:
        configurationManager.saveWorkspaceConfiguration(new WorkspaceConfiguration(distribution, workspaceGradleUserHome, offlineMode, buildScansEnabled, autoSync))
        BuildConfigurationFacade configuration = BuildConfigurationFacade.from(createInheritingBuildConfiguration(projectDir))

        then:
        configuration.gradleDistribution == distribution
        configuration.gradleUserHome == workspaceGradleUserHome
        configuration.overrideWorkspaceConfiguration == false
        configuration.buildScansEnabled == buildScansEnabled
        configuration.offlineMode == offlineMode
        configuration.autoSync == autoSync

        cleanup:
        configurationManager.saveWorkspaceConfiguration(orignalConfiguration)

        where:
        distribution                                                                 | offlineMode  | buildScansEnabled | autoSync
        GradleDistribution.fromBuild()                                               | false        | false             | true
        GradleDistribution.forVersion("3.2.1")                                       | false        | true              | false
        GradleDistribution.forLocalInstallation(new File('/').canonicalFile)         | true         | true              | false
        GradleDistribution.forRemoteDistribution(new URI('http://example.com/gd'))   | true         | false             | true
    }

    def "new build configuration can override workspace settings"(GradleDistribution distribution, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        setup:
        File projectDir = dir('project-dir')
        File projectGradleUserHome = dir('gradle-user-home').canonicalFile

        when:
        BuildConfigurationFacade configuration = BuildConfigurationFacade.from(createOverridingBuildConfiguration(projectDir, distribution, buildScansEnabled, offlineMode, autoSync, projectGradleUserHome))

        then:
        configuration.gradleDistribution == distribution
        configuration.gradleUserHome == projectGradleUserHome
        configuration.overrideWorkspaceConfiguration == true
        configuration.buildScansEnabled == buildScansEnabled
        configuration.offlineMode == offlineMode
        configuration.autoSync == autoSync

        where:
        distribution                                                                 | offlineMode  | buildScansEnabled | autoSync
        GradleDistribution.fromBuild()                                               | false        | false             | true
        GradleDistribution.forVersion("3.2.1")                                       | false        | true              | false
        GradleDistribution.forLocalInstallation(new File('/').canonicalFile)         | true         | true              | false
        GradleDistribution.forRemoteDistribution(new URI('http://example.com/gd'))   | true         | false             | true
    }

    def "can't load invalid build configuration"() {
        when:
        configurationManager.loadBuildConfiguration(new File('nonexistent'))

        then:
        thrown RuntimeException

        when:
        def projectDir = dir('project-dir'){
            dir('.settings') {
                file "${CorePlugin.PLUGIN_ID}.prefs", """override.workspace.settings=not_true_nor_false
connection.gradle.distribution=MODIFIED_DISTRO"""
            }
        }.canonicalFile
        BuildConfigurationFacade configuration = BuildConfigurationFacade.from(configurationManager.loadBuildConfiguration(projectDir))

        then:
        configuration.gradleDistribution == GradleDistribution.fromBuild()
        configuration.gradleUserHome == null
        configuration.overrideWorkspaceConfiguration == false
        configuration.buildScansEnabled == false
        configuration.offlineMode == false
        configuration.autoSync == false
    }

    def "load build configuration respecting workspaces settings"(GradleDistribution distribution, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        setup:
        File projectDir = dir('project-dir')
        WorkspaceConfiguration originalWsConfig = configurationManager.loadWorkspaceConfiguration()
        BuildConfiguration buildConfig = createInheritingBuildConfiguration(projectDir)
        File workspaceGradleUserHome = dir('gradle-user-home').canonicalFile

        when:
        configurationManager.saveBuildConfiguration(buildConfig)
        configurationManager.saveWorkspaceConfiguration(new WorkspaceConfiguration(distribution, workspaceGradleUserHome, offlineMode, buildScansEnabled, autoSync))
        buildConfig = configurationManager.loadBuildConfiguration(projectDir)
        BuildConfigurationFacade buildConfiguration = BuildConfigurationFacade.from(buildConfig)

        then:
        buildConfiguration.overrideWorkspaceConfiguration == false
        buildConfiguration.gradleDistribution == distribution
        buildConfiguration.gradleUserHome == workspaceGradleUserHome
        buildConfiguration.buildScansEnabled == buildScansEnabled
        buildConfiguration.offlineMode == offlineMode
        buildConfiguration.autoSync == autoSync

        cleanup:
        configurationManager.saveWorkspaceConfiguration(originalWsConfig)

        where:
        distribution                                                                 | offlineMode  | buildScansEnabled | autoSync
        GradleDistribution.fromBuild()                                               | false        | false             | true
        GradleDistribution.forVersion("3.2.1")                                       | false        | true              | false
        GradleDistribution.forLocalInstallation(new File('/').canonicalFile)         | true         | true              | false
        GradleDistribution.forRemoteDistribution(new URI('http://example.com/gd'))   | true         | false             | true
    }

    def "load build configuration overriding workspace settings"(GradleDistribution distribution, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        setup:
        File projectDir = dir('project-dir')
        WorkspaceConfiguration originalWsConfig = configurationManager.loadWorkspaceConfiguration()
        File projectGradleUserHome = dir('gradle-user-home').canonicalFile
        BuildConfiguration buildConfig = createOverridingBuildConfiguration(projectDir, distribution, buildScansEnabled, offlineMode, autoSync, projectGradleUserHome)

        when:
        configurationManager.saveBuildConfiguration(buildConfig)
        configurationManager.saveWorkspaceConfiguration(new WorkspaceConfiguration(GradleDistribution.fromBuild(), null, !buildScansEnabled, !offlineMode, !autoSync))
        buildConfig = configurationManager.loadBuildConfiguration(projectDir)
        BuildConfigurationFacade buildConfiguration = BuildConfigurationFacade.from(buildConfig)

        then:
        buildConfiguration.overrideWorkspaceConfiguration == true
        buildConfiguration.gradleDistribution == distribution
        buildConfig.gradleUserHome == Optional.of(projectGradleUserHome)
        buildConfiguration.buildScansEnabled == buildScansEnabled
        buildConfiguration.offlineMode == offlineMode
        buildConfiguration.autoSync == autoSync

        cleanup:
        configurationManager.saveWorkspaceConfiguration(originalWsConfig)

        where:
        distribution                                                                 | offlineMode  | buildScansEnabled | autoSync
        GradleDistribution.fromBuild()                                               | false        | false             | true
        GradleDistribution.forVersion("3.2.1")                                       | false        | true              | false
        GradleDistribution.forLocalInstallation(new File('/').canonicalFile)         | true         | true              | false
        GradleDistribution.forRemoteDistribution(new URI('http://example.com/gd'))   | true         | false             | true
    }

    private void setInvalidPreferenceOn(IProject project) {
        PreferenceStore preferences = PreferenceStore.forProjectScope(project, CorePlugin.PLUGIN_ID)
        preferences.write(BuildConfigurationPersistence.PREF_KEY_CONNECTION_GRADLE_DISTRIBUTION, 'I am error.')
        preferences.flush()
    }
}
