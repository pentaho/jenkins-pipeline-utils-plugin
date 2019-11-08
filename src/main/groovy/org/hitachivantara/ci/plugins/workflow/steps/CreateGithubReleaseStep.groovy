/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials
import com.cloudbees.plugins.credentials.domains.DomainRequirement
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.Extension
import hudson.model.TaskListener
import hudson.security.ACL
import hudson.util.Secret
import jenkins.model.Jenkins
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

class CreateGithubReleaseStep extends Step {

  String credentials
  String repository
  String name
  String text
  Boolean update = true

  @DataBoundConstructor
  CreateGithubReleaseStep() {
  }

  @DataBoundSetter
  void setCredentials(@Nonnull String credentials) {
    this.credentials = credentials
  }

  @DataBoundSetter
  void setRepository(@Nonnull String repository) {
    this.repository = repository
  }

  @DataBoundSetter
  void setName(String name) {
    this.name = name
  }

  @DataBoundSetter
  void setText(@Nonnull String text) {
    this.text = text
  }

  @DataBoundSetter
  void setUpdate(@Nonnull Boolean update) {
    this.update = update
  }

  @Override
  StepExecution start(StepContext stepContext) throws Exception {
    new Execution(stepContext, this)
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<Boolean> {
    private static final long serialVersionUID = 1L

    transient CreateGithubReleaseStep step
    transient PrintStream logger

    Execution(StepContext context, CreateGithubReleaseStep step) {
      super(context)
      this.step = step
      this.logger = context.get(TaskListener.class).getLogger()
    }

    @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
    @Override
    protected Boolean run() throws Exception {

      GHRelease release
      StandardCredentials userCredentials = getUsernamePasswordCredentials()

      logger.println("Parameters=[" +
        "Repository: ${step.repository}, " +
        "CredentialsId: ${step.credentials}, " +
        "Name: ${step.name}, " +
        "Update: ${step.update}, " +
        "Text: ${StringUtils.abbreviate(step.text, 100)}]")  // text might be very long

      if (userCredentials && userCredentials instanceof UsernamePasswordCredentials) {
        GHRepository repo = getRepository(userCredentials)

        release = repo.listReleases().asList().find { GHRelease rel ->
          rel.name == step.name
        }

        if (step.update && release != null) {
          release.delete()
        }

        release = repo
          .createRelease(step.name)
          .name(step.name)
          .body(step.text)
          .create()

        logger.println("Release '${step.name}' saved!")
      }
      return (release != null)
    }

    GHRepository getRepository(StandardCredentials userCredentials) {
      UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) userCredentials
      GitHub gh = GitHub.connect(
        usernamePasswordCredentials.getUsername(),
        Secret.toString(usernamePasswordCredentials.getPassword())
      )
      logger.println("Connecting to repository '${step.repository}'...")
      return gh.getRepository(step.repository)
    }

    @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
    private StandardCredentials getUsernamePasswordCredentials() {
      logger.println("Retrieving credentials with credentialsId '${step.credentials}'")

      return CredentialsProvider.lookupCredentials(
        StandardCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.<DomainRequirement> emptyList()
      ).find { StandardCredentials sc -> sc.id == step.credentials }
    }

  }

  @Extension
  static class Descriptor extends StepDescriptor {

    @Override
    Set<? extends Class<?>> getRequiredContext() {
      [TaskListener.class] as Set
    }

    @Override
    String getFunctionName() {
      'createGithubRelease'
    }

    @Override
    boolean takesImplicitBlockArgument() {
      false
    }

    @Override
    String getDisplayName() {
      'Creates a Github Release'
    }
  }
}
