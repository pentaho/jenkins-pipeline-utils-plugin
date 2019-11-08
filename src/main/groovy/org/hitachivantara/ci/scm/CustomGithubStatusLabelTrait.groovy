/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.scm

import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.Extension
import hudson.model.TaskListener
import jenkins.scm.api.SCMHeadCategory
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMBuilder
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import org.jenkinsci.plugins.github_branch_source.AbstractGitHubNotificationStrategy
import org.jenkinsci.plugins.github_branch_source.GitHubNotificationContext
import org.jenkinsci.plugins.github_branch_source.GitHubNotificationRequest
import org.jenkinsci.plugins.github_branch_source.GitHubSCMBuilder
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceContext
import org.kohsuke.stapler.DataBoundConstructor

class CustomGithubStatusLabelTrait extends SCMSourceTrait {

  @NonNull
  String label

  @DataBoundConstructor
  CustomGithubStatusLabelTrait(@CheckForNull String label) {
    this.label = label
  }

  @Override
  protected void decorateContext(SCMSourceContext<?, ?> context) {
    GitHubSCMSourceContext githubContext = (GitHubSCMSourceContext) context
    githubContext.withNotificationStrategies([new LabeledContextNotificationStrategy(label)])
  }

  @Override
  boolean includeCategory(@NonNull SCMHeadCategory category) {
    category.isUncategorized()
  }

  @Extension
  static class Descriptor extends SCMSourceTraitDescriptor {
    @Override
    String getDisplayName() {
      "Custom Github Check Label"
    }

    @Override
    Class<? extends SCMBuilder> getBuilderClass() {
      GitHubSCMBuilder.class
    }

    @Override
    Class<? extends SCMSourceContext> getContextClass() {
      GitHubSCMSourceContext.class
    }

    @Override
    Class<? extends SCMSource> getSourceClass() {
      GitHubSCMSource.class
    }
  }

  static final class LabeledContextNotificationStrategy extends AbstractGitHubNotificationStrategy {
    String label

    LabeledContextNotificationStrategy(String label) {
      this.label = label
    }

    @Override
    List<GitHubNotificationRequest> notifications(GitHubNotificationContext notificationContext, TaskListener listener) {
      [GitHubNotificationRequest.build(label,
        notificationContext.getDefaultUrl(listener),
        notificationContext.getDefaultMessage(listener),
        notificationContext.getDefaultState(listener),
        notificationContext.getDefaultIgnoreError(listener))]
    }

    @SuppressFBWarnings(value = "EQ_UNUSUAL", justification = "Groovy")
    @Override
    boolean equals(Object o) {
      if (this.is(o)) return true
      if (getClass() != o.class) return false

      LabeledContextNotificationStrategy that = (LabeledContextNotificationStrategy) o

      if (label != that.label) return false

      return true
    }

    @Override
    int hashCode() {
      return (label != null ? label.hashCode() : 0)
    }
  }
}