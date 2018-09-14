/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.configuration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.CorePlugin;

/**
 * Computes the Gradle build configuration based on whether the build inherits or overrides the
 * workspace configuration.
 *
 * @author Donat Csikos
 */
public final class BuildConfigurationFacade {

    private final BuildConfiguration buildConfiguration;
    private final WorkspaceConfiguration workspaceConfiguration;

    private File canonicalRootDirectory;

    private BuildConfigurationFacade(BuildConfiguration buildConfiguration, WorkspaceConfiguration workspaceConfiguration) {
        this.buildConfiguration = Preconditions.checkNotNull(buildConfiguration);
        this.workspaceConfiguration = workspaceConfiguration;
    }

    public WorkspaceConfiguration getWorkspaceConfiguration() {
        return this.workspaceConfiguration;
    }

    public File getRootProjectDirectory() {
        if (this.canonicalRootDirectory == null) {
            try {
                this.canonicalRootDirectory = this.buildConfiguration.getRootProjectDirectory().getCanonicalFile();
            } catch (IOException e) {
                this.canonicalRootDirectory = this.buildConfiguration.getRootProjectDirectory();
            }
        }
        return this.canonicalRootDirectory;
    }

    public boolean isOverrideWorkspaceConfiguration() {
        return this.buildConfiguration.isOverrideWorkspaceConfiguration();
    }

    public File getGradleUserHome() {
        if (this.buildConfiguration.isOverrideWorkspaceConfiguration()) {
            return this.buildConfiguration.getGradleUserHome().orElse(null);
        } else {
            return this.workspaceConfiguration.getGradleUserHome();
        }
    }

    public GradleDistribution getGradleDistribution() {
        if (this.buildConfiguration.isOverrideWorkspaceConfiguration()) {
            return this.buildConfiguration.getGradleDistribution();
        } else {
            return this.workspaceConfiguration.getGradleDistribution();
        }
    }

    public boolean isBuildScansEnabled() {
        if (this.buildConfiguration.isOverrideWorkspaceConfiguration()) {
            return this.buildConfiguration.isBuildScansEnabled();
        } else {
            return this.workspaceConfiguration.isBuildScansEnabled();
        }
    }

    public boolean isOfflineMode() {
        if (this.buildConfiguration.isOverrideWorkspaceConfiguration()) {
            return this.buildConfiguration.isOfflineMode();
        } else {
            return this.workspaceConfiguration.isOffline();
        }
    }

    public boolean isAutoSync() {
        if (this.buildConfiguration.isOverrideWorkspaceConfiguration()) {
            return this.buildConfiguration.isAutoSync();
        } else {
            return this.workspaceConfiguration.isAutoSync();
        }
    }

    public GradleArguments toGradleArguments() {
        return GradleArguments.from(getRootProjectDirectory(),
                getGradleDistribution(),
                getGradleUserHome(),
                null, /* Java home */
                isBuildScansEnabled(),
                isOfflineMode(), Collections.<String> emptyList() /* arguments*/,
                Collections.<String> emptyList() /* JVM arguments */);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BuildConfigurationFacade) {
            BuildConfigurationFacade other = (BuildConfigurationFacade) obj;
            return Objects.equal(this.buildConfiguration, other.buildConfiguration) && Objects.equal(this.workspaceConfiguration, other.workspaceConfiguration);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.buildConfiguration, this.workspaceConfiguration);
    }

    public static BuildConfigurationFacade from(BuildConfiguration buildConfiguration) {
        return new BuildConfigurationFacade(buildConfiguration, CorePlugin.configurationManager().loadWorkspaceConfiguration());
    }

    public BuildConfiguration getBuildConfiguration() {
        return this.buildConfiguration;
    }

    public BuildConfiguration asBuildConfiguration() {
        return BuildConfiguration.forRootProjectDirectory(getRootProjectDirectory())
            .overrideWorkspaceConfiguration(isOverrideWorkspaceConfiguration())
            .gradleDistribution(getGradleDistribution())
            .gradleUserHome(getGradleUserHome())
            .buildScansEnabled(isBuildScansEnabled())
            .offlineMode(isOfflineMode())
            .autoSync(isAutoSync())
            .build();
    }
}
