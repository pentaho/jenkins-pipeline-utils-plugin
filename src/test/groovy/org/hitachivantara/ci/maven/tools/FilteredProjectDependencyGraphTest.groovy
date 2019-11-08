/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import hudson.FilePath
import org.apache.maven.project.MavenProject
import spock.lang.Shared
import spock.lang.Specification

class FilteredProjectDependencyGraphTest extends Specification {
  @Shared
  MavenModule pom

  void setup() {
    /*
    Projects 1 and 4, have no upstream projects, so they can be built on their own.
    Project 2, needs project 1, so it can only be built after project 1.
    Project 3, needs project 2, so it can only be built after project 2.
    Project 5, needs project 2 and 4.
    */

    URL resources = getClass().getResource('/inter-module-dependency-project/pom.xml')
    File file = new File(resources.toURI())
    pom = MavenModule.builder().build(new FilePath(file))
  }

  def "test sorting projects"() {
    given:
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(pom, [])
    expect:
      dependencyGraph.activeProjects().size() == 5
      dependencyGraph.sortedProjects*.getName() == ['submodule-1', 'submodule-2-1', 'submodule-4', 'submodule-5-2-4', 'submodule-3-2']
      dependencyGraph.sortedProjectsByGroups == [['sub-1', 'sub-4'], ['sub-2'], ['sub-5', 'sub-3']]
  }

  def "test sorting filtered projects"() {
    given:
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(pom, ['sub-1', 'sub-2'])
    expect:
      dependencyGraph.activeProjects().size() == 5
      dependencyGraph.sortedProjects*.getName() == ['submodule-1', 'submodule-2-1']
      dependencyGraph.sortedProjectsByGroups == [['sub-1'], ['sub-2']]
  }

  def "test also-make and also-make-dependants"() {
    when:
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(pom, [])
      MavenProject project = new MavenProject(new ModelBuilder().build(pom.pom.parent.child(module)).effectiveModel)
    then:
      dependencyGraph.activeProjects().size() == 5
      dependencyGraph.getUpstreamProjects(project)*.name == upstream
      dependencyGraph.getDownstreamProjects(project)*.name == downstream

    where:
      module          || upstream                         | downstream
      'sub-3/pom.xml' || ['submodule-1', 'submodule-2-1'] | []
      'sub-2/pom.xml' || ['submodule-1']                  | ['submodule-5-2-4', 'submodule-3-2']
      'sub-1/pom.xml' || []                               | ['submodule-2-1', 'submodule-5-2-4', 'submodule-3-2']
  }
}
