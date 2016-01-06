/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.language.base.internal.model;

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.ModuleDependencySpec;
import org.gradle.platform.base.ProjectDependencySpec;

import java.util.Collections;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier.CONFIGURATION_API;
import static org.gradle.platform.base.internal.DefaultModuleDependencySpec.effectiveVersionFor;

public class DefaultLibraryLocalComponentMetaData extends DefaultLocalComponentMetaData {
    private static final String VERSION = "<local component>";
    private static final ExcludeRule[] EXCLUDE_RULES = new ExcludeRule[0];
    private static final String CONFIGURATION_COMPILE = "compile";

    public static DefaultLibraryLocalComponentMetaData newResolvedLibraryMetadata(
        LibraryBinaryIdentifier componentId,
        TaskDependency buildDependencies,
        Iterable<DependencySpec> dependencies,
        String defaultProject) {
        DefaultLibraryLocalComponentMetaData metadata = newDefaultLibraryLocalComponentMetadata(componentId, buildDependencies, CONFIGURATION_API);
        metadata.addDependencies(dependencies, defaultProject, CONFIGURATION_API);
        return metadata;
    }

    public static DefaultLibraryLocalComponentMetaData newResolvingLocalComponentMetadata(LibraryBinaryIdentifier componentId, String usage, Iterable<DependencySpec> dependencies) {
        DefaultLibraryLocalComponentMetaData metadata = newDefaultLibraryLocalComponentMetadata(componentId, new DefaultTaskDependency(), usage);
        metadata.addDependencies(dependencies, componentId.getProjectPath(), usage);
        return metadata;
    }

    private static DefaultLibraryLocalComponentMetaData newDefaultLibraryLocalComponentMetadata(LibraryBinaryIdentifier componentId, TaskDependency buildDependencies, String usage) {
        DefaultLibraryLocalComponentMetaData metaData = new DefaultLibraryLocalComponentMetaData(localModuleVersionIdentifierFor(componentId), componentId);
        metaData.addConfiguration(
            usage,
            String.format("Request metadata: %s", componentId.getDisplayName()),
            Collections.<String>emptySet(),
            Collections.singleton(usage),
            true,
            true,
            buildDependencies);
        return metaData;
    }

    private static DefaultModuleVersionIdentifier localModuleVersionIdentifierFor(LibraryBinaryIdentifier componentId) {
        return new DefaultModuleVersionIdentifier(componentId.getProjectPath(), componentId.getLibraryName(), VERSION);
    }

    private DefaultLibraryLocalComponentMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        super(id, componentIdentifier, Project.DEFAULT_STATUS);
    }

    private void addDependencies(Iterable<DependencySpec> dependencies, String projectPath, String usage) {
        for (DependencySpec dependency : dependencies) {
            addDependency(dependency, projectPath, usage);
        }
    }

    private void addDependency(DependencySpec dependency, String defaultProject, String usage) {
        DependencyMetaData metadata = dependency instanceof ModuleDependencySpec
            ? moduleDependencyMetadata((ModuleDependencySpec) dependency, usage)
            : projectDependencyMetadata((ProjectDependencySpec) dependency, defaultProject, usage);
        addDependency(metadata);
    }

    private DependencyMetaData moduleDependencyMetadata(ModuleDependencySpec moduleDependency, String usage) {
        ModuleVersionSelector requested = moduleVersionSelectorFrom(moduleDependency);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(requested);
        // TODO:DAZ: This hard-codes the assumption of a 'compile' configuration on the external module
        // Instead, we should be creating an API configuration for each resolved module
        return dependencyMetadataFor(selector, requested, usage, CONFIGURATION_COMPILE);
    }

    // TODO:DAZ:RBO: projectDependency should be transformed based on defaultProject (and other context) elsewhere.
    private DependencyMetaData projectDependencyMetadata(ProjectDependencySpec projectDependency, String defaultProject, String usage) {
        String projectPath = projectDependency.getProjectPath();
        if (isNullOrEmpty(projectPath)) {
            projectPath = defaultProject;
        }
        String libraryName = projectDependency.getLibraryName();
        // currently we use "null" as variant value, because there's only one variant: API
        ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
        DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(nullToEmpty(projectPath), nullToEmpty(libraryName), getId().getVersion());
        return dependencyMetadataFor(selector, requested, usage, usage);
    }

    private ModuleVersionSelector moduleVersionSelectorFrom(ModuleDependencySpec module) {
        return new DefaultModuleVersionSelector(module.getGroup(), module.getName(), effectiveVersionFor(module.getVersion()));
    }

    /**
     * This generates local dependency metadata for a dependency, but with a specific trick: normally, "usage" represents
     * the kind of dependency which is requested. For example, a library may require the API of a component, or the runtime of a component.
     * However, for external dependencies, there's no configuration called 'API' or 'runtime': we're mapping them to 'compile', which is
     * assumed to exist. Therefore, this method takes 2 arguments: one is the requested usage ("API") and the other is the mapped usage
     * ("compile"). For local libraries, both should be equal, but for external dependencies, they will be different.
     */
    private DependencyMetaData dependencyMetadataFor(ComponentSelector selector, ModuleVersionSelector requested, String usage, String usageMapping) {
        return new LocalComponentDependencyMetaData(
                selector, requested, usage, usageMapping,
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false, false, true);
    }

}
