/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.util.gradle;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.gradleware.tooling.toolingmodel.repository.internal.PathComparator;

/**
 * Represents a path in Gradle. The path can point to a project, task, etc.
 *
 * @author Etienne Studer
 */
public final class Path implements Comparable<Path> {

    private static final String PATH_SEPARATOR = ":";
    private static final Path ROOT_PATH = new Path(PATH_SEPARATOR);

    private final String path;

    private Path(String path) {
        this.path = Preconditions.checkNotNull(path);
        Preconditions.checkArgument(path.startsWith(PATH_SEPARATOR));
    }

    public String getPath() {
        return this.path;
    }

    /**
     * Returns a copy of this path with the last segment removed. If this path points to the root,
     * then the root path is returned.
     *
     * @return the new path
     */
    public Path dropLastSegment() {
        int lastPathChar = this.path.lastIndexOf(PATH_SEPARATOR);
        return lastPathChar <= 0 ? ROOT_PATH : new Path(this.path.substring(0, lastPathChar));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(Path other) {
        Preconditions.checkNotNull(other);
        return PathComparator.INSTANCE.compare(this.path, other.path);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Path that = (Path) other;
        return Objects.equal(this.path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.path);
    }

    public static Path from(String path) {
        return new Path(path);
    }

    /**
     * Singleton comparator to compare {@code Path} instances.
     */
    public enum Comparator implements java.util.Comparator<Path> {

        INSTANCE;

        @Override
        public int compare(Path o1, Path o2) {
            return o1.compareTo(o2);
        }

    }

}
