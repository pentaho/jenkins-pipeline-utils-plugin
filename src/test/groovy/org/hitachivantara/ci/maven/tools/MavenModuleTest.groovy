package org.hitachivantara.ci.maven.tools

import hudson.FilePath
import spock.lang.Shared
import spock.lang.Specification

class MavenModuleTest extends Specification {
  @Shared FilePath pom

  void setup() {
    URL resources = getClass().getResource('/multi-module-profiled-project/pom.xml')
    File file = new File(resources.toURI())
    pom = new FilePath(file)
  }

  def "test active modules"() {
    setup:
      MavenModule module = MavenModule.builder()
        .withActiveProfiles(activeProfiles)
        .withUserProperties(userProperties as Properties)
        .build(pom)

    when:
      List activeModules = module.getActiveModules()
      List allActiveModules = module.getAllActiveModules()

    then:
      activeModules == expectedActiveModules
      allActiveModules == expectedAllActiveModules

    where:
      activeProfiles | userProperties        | expectedActiveModules       | expectedAllActiveModules
      null           | null                  | ['sub-1', 'sub-2', 'sub-3'] | ['sub-1', 'sub-2', 'sub-3', 'sub-2/sub-1', 'sub-2/subsub-2', 'sub-2/sub-23', 'sub-3/sub-1', 'sub-3/sub-1/sub-11']
      ['profile-A']  | ['skipDefault': true] | ['sub-1', 'sub-2']          | ['sub-1', 'sub-2', 'sub-2/sub-1', 'sub-2/subsub-2', 'sub-2/sub-23']
  }

  def "test profile activation"() {
    given:
      MavenModule module = MavenModule.builder()
        .withActiveProfiles(['profile-B'])
        .withUserProperties(['skipDefault':true] as Properties)
        .build(pom)

    expect:
      module.getActiveModules() == ['sub-3']
      module.getAllActiveModules() == ['sub-3', 'sub-3/sub-1', 'sub-3/sub-1/sub-11']
  }

  def "test deep profiles"() {
    setup:
      URL resources = getClass().getResource('/deep-profiled-project/pom.xml')
      FilePath pom = new FilePath(new File(resources.toURI()))
      MavenModule module = MavenModule.builder()
        .withActiveProfiles(activeProfiles)
        .withUserProperties(userProperties as Properties)
        .build(pom)

    when:
      List activeModules = module.getActiveModules()
      List allActiveModules = module.getAllActiveModules()

    then:
      activeModules == expectedActiveModules
      allActiveModules == expectedAllActiveModules

    where:
      activeProfiles           | userProperties        | expectedActiveModules | expectedAllActiveModules
      ['base', 'other', 'low'] | ['skipDefault': true] | ['p1', 'p2', 'p3']    | ['p1', 'p2', 'p3', 'p2/sub-low', 'p3/sub-low']
      ['base', 'low']          | null                  | ['p1', 'p2']          | ['p1', 'p2', 'p2/sub-low']
  }

  def "test profile negation"() {
    given:
      MavenModule module = MavenModule.builder()
        .withInactiveProfiles(['profile-B'])
        .build(pom)

    expect:
      module.getActiveModules() == ['sub-1', 'sub-2']
      module.getAllActiveModules() == ['sub-1', 'sub-2', 'sub-2/sub-1', 'sub-2/subsub-2', 'sub-2/sub-23']
  }
}
