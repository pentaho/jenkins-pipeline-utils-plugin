/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.scm

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import hudson.model.Action
import org.kohsuke.stapler.export.Exported
import org.kohsuke.stapler.export.ExportedBean

@CompileStatic
@ExportedBean(defaultVisibility = 999)
class SCMData implements Action, Serializable {
  private static final long serialVersionUID = 1L

  String scmUrl
  String commitId
  String branch

  SCMData() {}

  SCMData(String scmUrl, String branch, String sha) {
    this.scmUrl = scmUrl
    this.branch = branch
    this.commitId = sha
  }

  @Exported
  String getScmUrl() {
    scmUrl
  }

  @Exported
  String getCommitId() {
    commitId
  }

  @Exported
  String getBranch() {
    branch
  }


  @Override
  String getIconFileName() {
    return null
  }

  @Override
  String getDisplayName() {
    return "SCM Data : $scmUrl@$branch"
  }

  @Override
  String getUrlName() {
    return null
  }

  @Override
  int hashCode() {
    Objects.hash(scmUrl, branch, commitId)
  }

  @Override
  boolean equals(Object obj) {
    if (!(obj instanceof SCMData)) return false

    SCMData other = (SCMData) obj

    return Objects.equals(scmUrl, other.scmUrl) &&
        Objects.equals(branch, other.branch) &&
        Objects.equals(commitId, other.commitId)
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  String toString() {
    "${super.toString()} scmUrl=$scmUrl,branch=$branch,revision=$commitId"
  }
}