package org.eclipse.buildship.core.internal.configuration

import spock.lang.Shared
import spock.lang.Subject

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.configuration.BuildConfiguration
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.test.fixtures.WorkspaceSpecification

class BuildConfigurationPersistenceTest extends WorkspaceSpecification {

    @Shared
    @Subject
    BuildConfigurationPersistence persistence = new BuildConfigurationPersistence()

    IProject project
    File projectDir

    def setup() {
        project = newProject("sample-project")
        projectDir = dir("external")
    }

    def "save validates input"() {
        when:
        persistence.saveBuildConfiguration(project, null)

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration((File) null, validProperties(projectDir))

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration(projectDir, null)

        then:
        thrown NullPointerException

        when:
        persistence.saveBuildConfiguration((IProject) null, validProperties(project))

        then:
        thrown NullPointerException
    }

    def "can save and read preferences for workspace project"() {
        setup:
        BuildConfiguration properties = validProperties(project)
        persistence.saveBuildConfiguration(project, properties)

        expect:
        persistence.readBuildConfiguratonProperties(project) == properties
    }

    def "can save and read preferences for external project"() {
        setup:
        BuildConfiguration properties = validProperties(projectDir)
        persistence.saveBuildConfiguration(projectDir, properties)

        expect:
        persistence.readBuildConfiguratonProperties(projectDir) == properties
    }

    def "reading build configuration validates input"() {
        when:
        persistence.readBuildConfiguratonProperties((IProject) null)

        then:
        thrown NullPointerException

        when:
        persistence.readBuildConfiguratonProperties((File) null)

        then:
        thrown NullPointerException
    }

    def "Reading nonexisting build configuration returns default"() {
        when:
        BuildConfiguration properties = persistence.readBuildConfiguratonProperties(project)

        then:
        properties.rootProjectDirectory == project.location.toFile()
        properties.overrideWorkspaceConfiguration == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == Optional.empty()
        properties.buildScansEnabled == false
        properties.offlineMode == false
        properties.autoSync == false

        when:
        properties = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        properties.rootProjectDirectory == projectDir
        properties.overrideWorkspaceConfiguration == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == Optional.empty()
        properties.buildScansEnabled == false
        properties.offlineMode == false
        properties.autoSync == false
    }

    def "Reading broken build configuration results in using default settings"() {
        setup:
        String prefsFileContent = """override.workspace.settings=not_true_nor_false
connection.gradle.distribution=MODIFIED_DISTRIBUTION"""
        fileTree(project.location.toFile()) { dir('.settings') { file "${CorePlugin.PLUGIN_ID}.prefs", prefsFileContent } }
        fileTree(projectDir) { dir('.settings') { file "${CorePlugin.PLUGIN_ID}.prefs", prefsFileContent } }
        project.refreshLocal(IResource.DEPTH_INFINITE, null)

        when:
        BuildConfiguration properties = persistence.readBuildConfiguratonProperties(project)

        then:
        properties.overrideWorkspaceConfiguration == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == Optional.empty()
        properties.buildScansEnabled == false
        properties.offlineMode == false
        properties.autoSync == false

        when:
        properties = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        properties.overrideWorkspaceConfiguration == false
        properties.gradleDistribution == GradleDistribution.fromBuild()
        properties.gradleUserHome == Optional.empty()
        properties.buildScansEnabled == false
        properties.offlineMode == false
        properties.autoSync == false
    }

    def "If workspace override is not set then overridden configuration properties are ignored"() {
        setup:
        BuildConfiguration properties = validProperties(projectDir, GradleDistribution.forVersion('2.0'), dir('gradle-user-home'), false, true, true, true)
        persistence.saveBuildConfiguration(project, properties)
        persistence.saveBuildConfiguration(projectDir, properties)

        when:
        BuildConfiguration importedProjectProperties = persistence.readBuildConfiguratonProperties(project)
        BuildConfiguration externalProjectProperties = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        importedProjectProperties.overrideWorkspaceConfiguration == false
        importedProjectProperties.gradleDistribution == GradleDistribution.fromBuild()
        importedProjectProperties.gradleUserHome == Optional.empty()
        importedProjectProperties.buildScansEnabled == false
        importedProjectProperties.offlineMode == false
        importedProjectProperties.autoSync == false

        externalProjectProperties.overrideWorkspaceConfiguration == false
        externalProjectProperties.gradleDistribution == GradleDistribution.fromBuild()
        externalProjectProperties.gradleUserHome == Optional.empty()
        externalProjectProperties.buildScansEnabled == false
        externalProjectProperties.offlineMode == false
        importedProjectProperties.autoSync == false
    }

    def "If workspace override is set then overridden configuration properties are persisted"(GradleDistribution distribution, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        setup:
        File gradleUserHome = dir('gradle-user-home').canonicalFile
        BuildConfiguration properties = validProperties(projectDir, distribution, gradleUserHome, true, buildScansEnabled, offlineMode, autoSync)
        persistence.saveBuildConfiguration(project, properties)
        persistence.saveBuildConfiguration(projectDir, properties)

        when:
        BuildConfiguration importedProjectProperties = persistence.readBuildConfiguratonProperties(project)
        BuildConfiguration externalProjectProperties = persistence.readBuildConfiguratonProperties(projectDir)

        then:
        importedProjectProperties.overrideWorkspaceConfiguration == true
        importedProjectProperties.gradleDistribution == distribution
        importedProjectProperties.gradleUserHome == Optional.of(gradleUserHome)
        importedProjectProperties.buildScansEnabled == buildScansEnabled
        importedProjectProperties.offlineMode == offlineMode
        importedProjectProperties.autoSync == autoSync

        externalProjectProperties.overrideWorkspaceConfiguration == true
        externalProjectProperties.gradleDistribution == distribution
        externalProjectProperties.gradleUserHome == Optional.of(gradleUserHome)
        externalProjectProperties.buildScansEnabled == buildScansEnabled
        externalProjectProperties.offlineMode == offlineMode
        externalProjectProperties.autoSync == autoSync

        where:
        distribution                         | buildScansEnabled | offlineMode | autoSync
        GradleDistribution.forVersion('3.5') | false             | false       | true
        GradleDistribution.forVersion('3.4') | false             | true        | false
        GradleDistribution.forVersion('3.3') | true              | false       | false
        GradleDistribution.forVersion('3.2') | true              | true        | true
    }

    def "pathToRoot methods validate input"() {
        when:
        persistence.readPathToRoot(null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot(project, null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot(projectDir, null)

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot((IProject) null, '.')

        then:
        thrown NullPointerException

        when:
        persistence.savePathToRoot((File) null, '.')

        then:
        thrown NullPointerException

        when:
        persistence.deletePathToRoot((IProject) null)

        then:
        thrown NullPointerException

        when:
        persistence.deletePathToRoot((File) null)

        then:
        thrown NullPointerException
    }

    def "reading nonexisting path to root results in runtime exception"() {
        when:
        persistence.readPathToRoot(project)

        then:
        thrown RuntimeException

        when:
        persistence.readPathToRoot(projectDir)

        then:
        thrown RuntimeException
    }

    def "can read and save path to root on workspace project"() {
        when:
        persistence.savePathToRoot(project, 'path-to-root')

        then:
        persistence.readPathToRoot(project) == 'path-to-root'
    }

    def "can read and save path to root on external project"() {
        when:
        persistence.savePathToRoot(projectDir, 'path-to-root')

        then:
        persistence.readPathToRoot(projectDir) == 'path-to-root'
    }

    def "can delete path to root on workspace project"() {
        setup:
        persistence.savePathToRoot(project, 'path-to-root')
        persistence.deletePathToRoot(project)

        when:
        persistence.readPathToRoot(project)

        then:
        thrown RuntimeException
    }

    def "can delete path to root on external project"() {
        setup:
        persistence.savePathToRoot(projectDir, 'path-to-root')
        persistence.deletePathToRoot(projectDir)

        when:
        persistence.readPathToRoot(projectDir)

        then:
        thrown RuntimeException
    }

    private def validProperties(IProject project) {
        validProperties(project.getLocation().toFile())
    }

    private def validProperties(File projectDir) {
        BuildConfiguration
                .forRootProjectDirectory(projectDir)
                .build()
    }

    private def validProperties(File projectDir, GradleDistribution distribution, File gradleUserHome, boolean overrideWorkspaceSettings, boolean buildScansEnabled, boolean offlineMode, boolean autoSync) {
        BuildConfiguration.forRootProjectDirectory(projectDir)
                .gradleDistribution(distribution)
                .gradleUserHome(gradleUserHome)
                .overrideWorkspaceConfiguration(overrideWorkspaceSettings)
                .buildScansEnabled(buildScansEnabled)
                .offlineMode(offlineMode)
                .autoSync(autoSync)
                .build()
    }
}
