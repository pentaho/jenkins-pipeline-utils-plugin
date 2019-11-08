/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted
import hudson.FilePath

import java.nio.file.Path
import java.nio.file.Paths

class MavenModule implements Serializable {
  private static final long serialVersionUID = 3L

  @Whitelisted String id
  @Whitelisted String path
  @Whitelisted FilePath pom
  @Whitelisted int depth
  @Whitelisted MavenModule parent
  @Whitelisted List<MavenModule> modules

  @Whitelisted List<String> activeProfiles
  @Whitelisted List<String> inactiveProfiles
  @Whitelisted Properties userProperties

  protected MavenModule(String id, FilePath pom, MavenModule parent = null) {
    this.id = id
    this.pom = pom
    this.parent = parent
    this.depth = (!parent ? 0 : parent.depth + 1)

    if (!parent) {
      this.path = "."
    } else {
      Path parentBaseDir = Paths.get(parent.pom.parent.remote)
      Path moduleBaseDir = Paths.get(pom.parent.remote)
      this.path = parentBaseDir.normalize().relativize(moduleBaseDir.normalize())
    }
  }

  static ModuleBuilder builder() {
    new ModuleBuilder()
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getAllActiveModules() {
    allModules.collect { MavenModule module -> module.fullPath }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getActiveModules() {
    modules.collect { MavenModule module -> module.fullPath }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<MavenModule> getAllModules() {
    modules + modules.collectMany { MavenModule module -> module.getAllModules() }
  }

  @Whitelisted
  String getFullPath() {
    if (!parent) {
      return "."
    }
    return (parent.fullPath == '.' ? '' : parent.fullPath + File.separator) + path
  }

  @Whitelisted
  MavenModule getRoot() {
    !parent ? this : parent.root
  }

  @SuppressFBWarnings(value = "EQ_UNUSUAL", justification = "Groovy man!")
  @Whitelisted
  boolean equals(o) {
    if (this.is(o)) return true
    if (getClass() != o.class) return false

    MavenModule that = (MavenModule) o

    return id == that.id
  }

  @Whitelisted
  int hashCode() {
    return (id != null ? id.hashCode() : 0)
  }

  @Whitelisted
  String toString() {
    "MavenModule{" +
      "id=" + id +
      ", path=" + path +
      ", pom=" + pom +
      ", parent=" + (!parent ? "<none>" : parent.id) +
      '}'
  }
}
