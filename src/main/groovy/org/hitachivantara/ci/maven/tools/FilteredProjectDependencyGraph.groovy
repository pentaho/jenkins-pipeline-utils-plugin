/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.FilePath
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.execution.ProjectDependencyGraph
import org.apache.maven.graph.DefaultProjectDependencyGraph
import org.apache.maven.model.Model
import org.apache.maven.project.MavenProject
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import java.nio.file.Paths

class FilteredProjectDependencyGraph implements Serializable {
  private static final long serialVersionUID = 2L

  @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
  private transient MavenModule module
  private List<String> projectList

  FilteredProjectDependencyGraph(MavenModule module, List<String> projectList) {
    this.module = module
    this.projectList = projectList
  }

  private ProjectDependencyGraph reactorDependencyGraph() {
    new DefaultProjectDependencyGraph(activeProjects())
  }

  @Whitelisted
  List<MavenProject> activeProjects() {
    return getMavenProjects(module.getAllActiveModules())
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  private List<MavenProject> getMavenProjects(Collection<String> modules) {
    Map<String, MavenProject> projects = [:]

    Map<String, ModelBuilder.Result> map = modules.collectEntries { String project ->
      FilePath modulePom = module.pom.parent.child("$project/pom.xml")
      ModelBuilder.Result result = new ModelBuilder()
        .withActiveProfiles(module.activeProfiles)
        .withInactiveProfiles(module.inactiveProfiles)
        .withUserProperties(module.userProperties)
        .build(modulePom)

      MavenProject mavenProject = new MavenProject(result.effectiveModel)
      projects.put(result.modelIds.get(0), mavenProject)

      return [(result.modelIds.get(0)): result]
    }

    List<MavenProject> mavenProjects = map.collect { String key, ModelBuilder.Result result ->
      MavenProject project = projects.get(key)
      initProject(project, projects, result)
    }

    return mavenProjects
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  private List<MavenProject> collectRecursive(MavenProject project) {
    def result = [project]
    if (!project.collectedProjects) return result
    result.addAll(project.collectedProjects?.collectMany { collectRecursive(it) })
    return result
  }

  private MavenProject initProject(MavenProject project, Map<String, MavenProject> projects, ModelBuilder.Result result) {
    Model model = result.getEffectiveModel()
    project.originalModel = result.rawModel
    project.file = model.pomFile

    Model parentModel = result.modelIds.size() > 1 && result.modelIds.get(1) ? result.rawModels[result.modelIds.get(1)] : null

    if (parentModel) {
      String parentModelId = result.modelIds.get(1)

      MavenProject parent = projects.get(parentModelId)

      File parentPomFile = result.rawModels[parentModelId].pomFile
      project.parentFile = parentPomFile
      project.parent = parent
      if (parent) {
        if (!parent.collectedProjects) parent.collectedProjects = []
        parent.collectedProjects.add(project)
      }
    }
    project
  }

  @Whitelisted
  List<MavenProject> getSortedProjects() {
    ProjectDependencyGraph projectDependencyGraph = reactorDependencyGraph()
    applyFilter(projectDependencyGraph.sortedProjects)
  }

  @Whitelisted
  List<MavenProject> getDownstreamProjects(MavenProject mavenProject, boolean transitive = true) {
    ProjectDependencyGraph projectDependencyGraph = reactorDependencyGraph()
    applyFilter(projectDependencyGraph.getDownstreamProjects(mavenProject, transitive))
  }

  @Whitelisted
  List<MavenProject> getUpstreamProjects(MavenProject mavenProject, boolean transitive = true) {
    ProjectDependencyGraph projectDependencyGraph = reactorDependencyGraph()
    applyFilter(projectDependencyGraph.getUpstreamProjects(mavenProject, transitive))
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<List<String>> getSortedProjectsByGroups() {
    List<MavenProject> sortedProjects = getSortedProjects()
    def grpProjects = sortedProjects
        .groupBy({ it.projectReferences }, { getAncestorMavenProject(it) })
        .values()
        .collect { it.values() }

    def result = [[]]
    grpProjects*.each { List<MavenProject> pl ->
      List<String> upstreams = pl.collectMany { it.projectReferences.keySet() }
      List<String> prevs = result.last().collectNested { MavenProject p ->
        ArtifactUtils.key(p.groupId, p.artifactId, p.version)
      }.flatten() as List<String>

      if (!prevs.any { it in upstreams }) result.last() << pl
      else result << [pl]
    }

    return result.collectNested { getMavenProjectRelativePath(it) }.collect { it*.join(',') }
  }

  private String getMavenProjectRelativePath(MavenProject project) {
    Paths.get(module.pom.parent.remote).relativize(project.basedir.toPath())
  }

  private MavenProject getAncestorMavenProject(MavenProject project) {
    while (project.parent) {
      project = project.parent
    }
    return project
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<MavenProject> applyFilter(List<MavenProject> mavenProjects) {
    if (!projectList) return mavenProjects

    Map<MavenProject, ?> whiteList =
        getMavenProjects(projectList).collectEntries { [(it): null] }

    return mavenProjects.findAll { MavenProject project ->
      whiteList.containsKey(project)
    }
  }

  @Whitelisted
  String toString() {
    getSortedProjects().toString()
  }
}
