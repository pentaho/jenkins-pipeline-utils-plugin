/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import hudson.FilePath
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.model.Model

@Builder(builderStrategy = SimpleStrategy, prefix = 'with')
class ModuleBuilder {

  List<String> activeProfiles
  List<String> inactiveProfiles
  Map userProperties
  MavenModule parent

  @SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
  MavenModule build(FilePath pom) {
    ModelBuilder.Result result = new ModelBuilder()
      .withActiveProfiles(activeProfiles)
      .withInactiveProfiles(inactiveProfiles)
      .withUserProperties(userProperties)
      .build(pom)

    Model model = result.effectiveModel
    String id = ArtifactUtils.versionlessKey(model.groupId, model.artifactId)

    MavenModule module = new MavenModule(id, pom.absolutize(), parent)

    module.activeProfiles = activeProfiles
    module.inactiveProfiles = inactiveProfiles
    module.userProperties = userProperties

    module.modules = model.modules.collect { String modulePath ->
      new ModuleBuilder()
        .withActiveProfiles(activeProfiles)
        .withInactiveProfiles(inactiveProfiles)
        .withUserProperties(userProperties)
        .withParent(module)
        .build(pom.parent.child("${modulePath}/pom.xml"))
    }

    return module
  }
}
