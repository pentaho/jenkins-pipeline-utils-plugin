package org.hitachivantara.ci.maven.tools

import hudson.FilePath
import spock.lang.Shared
import spock.lang.Specification

class MultiDependencyGraphTest extends Specification {
  @Shared
  FilePath pom1, pom2

  void setupSpec() {
    URL resources1 = getClass().getResource('/multi-module-profiled-project/pom.xml')
    URL resources2 = getClass().getResource('/deep-profiled-project/pom.xml')
    pom1 = new FilePath(new File(resources1.toURI()))
    pom2 = new FilePath(new File(resources2.toURI()))
  }

  def "test sorting multi projects"() {
    given:
      MavenModule module = MavenModule.builder().build(pom1)
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(module, [])

    expect:
      dependencyGraph.sortedProjectsByGroups == [['sub-1', 'sub-2,sub-2/sub-1', 'sub-3,sub-3/sub-1,sub-3/sub-1/sub-11'],
                                                 ['sub-2/subsub-2'],
                                                 ['sub-2/sub-23']]
  }

  def "test sorting profiles projects"() {
    given:
      MavenModule module = MavenModule.builder()
        .withActiveProfiles(['base', 'other', 'low', 'other2'])
        .withUserProperties(['skipDefault': true] as Properties)
        .build(pom2)
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(module, [])

    expect:
      dependencyGraph.sortedProjectsByGroups == [['p1', 'p2,p2/sub-low', 'p3,p3/sub-low', 'p4,p4/b,p4/b/a'],
                                                 ['p4/a']]
  }

  def "test sorting negated profile projects"() {
    given:
      MavenModule module = MavenModule.builder()
        .withInactiveProfiles(['profile-B'])
        .build(pom1)
      FilteredProjectDependencyGraph dependencyGraph = new FilteredProjectDependencyGraph(module, [])

    expect:
      dependencyGraph.sortedProjectsByGroups == [['sub-1', 'sub-2,sub-2/sub-1'],
                                                 ['sub-2/subsub-2'],
                                                 ['sub-2/sub-23']]
  }
}
