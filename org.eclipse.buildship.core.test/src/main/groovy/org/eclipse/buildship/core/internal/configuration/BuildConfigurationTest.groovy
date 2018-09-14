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
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification;

@SuppressWarnings("GroovyAccessibility")
class BuildConfigurationTest extends ProjectSynchronizationSpecification {

    def "can save and load build configuration"() {
        setup:
        File projectDir = dir('project-dir').canonicalFile
        BuildConfiguration configuration = createOverridingBuildConfiguration(projectDir, GradleDistribution.forVersion('2.0'))

        when:
        configurationManager.saveBuildConfiguration(configuration)
        configuration = configurationManager.loadBuildConfiguration(projectDir)

        then:
        configuration.rootProjectDirectory == projectDir
        configuration.gradleDistribution == GradleDistribution.forVersion('2.0')
        configuration.gradleUserHome == Optional.empty()
        configuration.overrideWorkspaceConfiguration == true
        configuration.buildScansEnabled == false
        configuration.offlineMode == false
        configuration.autoSync == false
    }

    def "can load build configuration from closed projects"() {
        setup:
        IProject project = newProject('project')
        File projectDir = project.location.toFile()
        BuildConfiguration configuration = createOverridingBuildConfiguration(projectDir, GradleDistribution.forVersion('2.0'))

        when:
        project.close(new NullProgressMonitor())
        configurationManager.saveBuildConfiguration(configuration)
        configuration = configurationManager.loadBuildConfiguration(projectDir)

        then:
        configuration.rootProjectDirectory == projectDir
        configuration.gradleDistribution == GradleDistribution.forVersion('2.0')
        configuration.gradleUserHome == Optional.empty()
        configuration.overrideWorkspaceConfiguration == true
        configuration.buildScansEnabled == false
        configuration.offlineMode == false
        configuration.autoSync == false
    }
}
