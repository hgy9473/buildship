package org.eclipse.buildship.core

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration
import org.eclipse.buildship.core.internal.extension.ExtensionManager
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class ProjectConfiguratorTest extends BaseProjectConfiguratorTest {

    ProjectConfigurator configurator

    def setup() {
        configurator = registerConfigurator(Mock(ProjectConfigurator))
    }

    def "Configurator initialized once per synchronization"() {
        setup:
        File location = dir('ProjectConfiguratorTest_1')

        when:
        importAndWait(location)

        then:
        1 * configurator.init(_, _)
    }

    def "configure() called for each project"() {
        setup:
        File location = dir('ProjectConfiguratorTest') {
            file "settings.gradle", """
                rootProject.name = 'root'
                include 'sub'
            """
            dir 'sub'
        }

        when:
        importAndWait(location)

        then:
        1 * configurator.configure({ ProjectContext pc -> pc.project.name == 'root' }, _)
        1 * configurator.configure({ ProjectContext pc -> pc.project.name == 'sub' }, _)
    }

    def "unconfigure() called for each removed project"() {
        setup:
        File settingsFile = null
        File location = dir('ProjectConfiguratorTest') {
            settingsFile = file "settings.gradle", """
                rootProject.name = 'root'
                include 'sub1'
                include 'sub2'
            """
            dir 'sub1'
            dir 'sub2'
        }
        importAndWait(location)
        new File(location, 'settings.gradle').text = "rootProject.name = 'root'"

        when:
        synchronizeAndWait(location)

        then:
        1 * configurator.unconfigure({ ProjectContext pc -> pc.project.name == 'sub1' }, _)
        1 * configurator.unconfigure({ ProjectContext pc -> pc.project.name == 'sub2' }, _)
    }
}
