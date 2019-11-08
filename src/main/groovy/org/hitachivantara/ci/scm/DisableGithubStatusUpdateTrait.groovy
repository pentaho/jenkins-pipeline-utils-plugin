/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.scm

import hudson.Extension
import jenkins.scm.api.SCMHeadCategory
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMBuilder
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import org.jenkinsci.plugins.github_branch_source.GitHubSCMBuilder
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceContext
import org.kohsuke.stapler.DataBoundConstructor

import javax.annotation.Nonnull

class DisableGithubStatusUpdateTrait extends SCMSourceTrait {

  @DataBoundConstructor
  DisableGithubStatusUpdateTrait() { }

  protected void decorateContext(SCMSourceContext context) {
    GitHubSCMSourceContext ghContext = (GitHubSCMSourceContext) context
    ghContext.withNotificationsDisabled(true)
  }

  boolean includeCategory(@Nonnull SCMHeadCategory category) {
    category.isUncategorized()
  }

  @Extension
  static class Descriptor extends SCMSourceTraitDescriptor {

    String getDisplayName() {
      'Disable Github status update'
    }

    Class<? extends SCMBuilder> getBuilderClass() {
      GitHubSCMBuilder.class
    }

    Class<? extends SCMSourceContext> getContextClass() {
      GitHubSCMSourceContext.class
    }

    Class<? extends SCMSource> getSourceClass() {
      GitHubSCMSource.class
    }
  }
}
