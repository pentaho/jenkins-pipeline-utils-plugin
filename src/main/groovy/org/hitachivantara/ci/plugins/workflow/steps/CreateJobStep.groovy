/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import com.cloudbees.hudson.plugins.folder.Folder
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger
import hudson.Extension
import hudson.model.Item
import hudson.model.TaskListener
import hudson.model.TopLevelItemDescriptor
import jenkins.branch.BranchSource
import jenkins.model.Jenkins
import jenkins.model.ModifiableTopLevelItemGroup
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy
import net.gleske.scmfilter.impl.trait.WildcardSCMHeadFilterTrait
import org.hitachivantara.ci.scm.DisableGithubStatusUpdateTrait
import org.hitachivantara.ci.scm.CustomGithubStatusLabelTrait
import org.jenkinsci.plugins.github_branch_source.BranchDiscoveryTrait
import org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait
import org.jenkinsci.plugins.inlinepipeline.InlineDefinitionBranchProjectFactory
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

class CreateJobStep extends Step implements Serializable {
  private static final long serialVersionUID = 1L

  /**
   * Name of the job to create
   */
  String name

  /**
   * Folder path where to create the job
   */
  String folder = ''

  /**
   * Pipeline script to use on the job
   */
  String script = ''

  /**
   * If the job to create is a multibranch
   */
  Boolean multibranch = false

  /**
   * SCM additional configurations, used to define extra behaviour on multibranch jobs
   */
  ScmConfig scmConfig = new ScmConfig()

  @DataBoundConstructor
  CreateJobStep(String name) {
    this.name = name
  }

  @DataBoundSetter
  void setMultibranch(boolean multibranch) {
    this.multibranch = multibranch
  }

  @DataBoundSetter
  void setFolder(String folder) {
    this.folder = folder
  }

  @DataBoundSetter
  void setScript(String script) {
    this.script = script
  }

  @DataBoundSetter
  void setScmConfig(Map scmConfig) {
    this.scmConfig = new ScmConfig(scmConfig)
  }

  StepExecution start(StepContext context) throws Exception {
    new Execution(this, context)
  }

  static class Execution extends SynchronousNonBlockingStepExecution<Boolean> {
    private static final long serialVersionUID = 1L

    transient PrintStream logger
    CreateJobStep step


    Execution(CreateJobStep step, StepContext context) {
      super(context)
      this.step = step
      this.logger = context.get(TaskListener.class).getLogger()
    }

    protected Boolean run() throws Exception {
      if (step.multibranch) {
        createOrUpdateMultibranchJob()
      } else {
        createOrUpdateJob()
      }

      return true
    }

    private void createOrUpdateMultibranchJob() {
      Jenkins jenkins = Jenkins.get()
      TopLevelItemDescriptor jobDescriptor = jenkins.getDescriptor(WorkflowMultiBranchProject.class)

      ModifiableTopLevelItemGroup folder = findOrCreateFolder()
      Item foundItem = folder.getItem(step.name)

      WorkflowMultiBranchProject job

      // job does not exist
      if (!foundItem) {
        job = folder.createProject(jobDescriptor, step.name, true)
      }
      // there is something, but not what we want
      else if (!(foundItem instanceof WorkflowMultiBranchProject)) {
        logger.println("A different type item named '${step.name}' already exists, it will be deleted!")

        foundItem.delete()
        job = folder.createProject(jobDescriptor, step.name, true)
      }
      // the job exists
      else {
        job = foundItem as WorkflowMultiBranchProject
      }

      ScmConfig scmConfig = step.scmConfig
      GitHubSCMSource scmSource = new GitHubSCMSource(scmConfig.organization, scmConfig.repository)
      scmSource.setId(scmConfig.organization + '/' + scmConfig.repository)
      scmSource.setCredentialsId(scmConfig.credentials)

      List scmTraits = [
        new BranchDiscoveryTrait(true, true),
      ]

      if (scmConfig.branches) {
        scmTraits << new WildcardSCMHeadFilterTrait(scmConfig.branches, '')
      }

      if (scmConfig.prScan) {
        scmTraits << new ForkPullRequestDiscoveryTrait(
          (scmConfig.prMerge ? [ChangeRequestCheckoutStrategy.MERGE] : [ChangeRequestCheckoutStrategy.HEAD]) as Set,
          new ForkPullRequestDiscoveryTrait.TrustContributors()
        )
        scmTraits << new OriginPullRequestDiscoveryTrait(
          (scmConfig.prMerge ? [ChangeRequestCheckoutStrategy.MERGE] : [ChangeRequestCheckoutStrategy.HEAD]) as Set)
      }

      if (!scmConfig.prReportStatus) {
        scmTraits << new DisableGithubStatusUpdateTrait()
      }

      if (scmConfig.prStatusLabel) {
        scmTraits << new CustomGithubStatusLabelTrait(scmConfig.prStatusLabel)
      }

      scmSource.setTraits(scmTraits)
      job.setSourcesList([new BranchSource(scmSource)])

      if (step.script) {
        job.setProjectFactory(new InlineDefinitionBranchProjectFactory(
          script: step.script,
          markerFile: scmConfig.markerFile,
          sandbox: true
        ))
      }

      if (scmConfig.scanInterval) {
        job.addTrigger(new PeriodicFolderTrigger(scmConfig.scanInterval))
      }
    }

    private void createOrUpdateJob() {
      Jenkins jenkins = Jenkins.get()
      TopLevelItemDescriptor jobDescriptor = jenkins.getDescriptor(WorkflowJob.class)

      ModifiableTopLevelItemGroup folder = findOrCreateFolder()
      Item foundItem = folder.getItem(step.name)

      WorkflowJob job

      // job does not exist
      if (!foundItem) {
        job = folder.createProject(jobDescriptor, step.name, true)
      }
      // there is something, but not what we want
      else if (!(foundItem instanceof WorkflowJob)) {
        logger.println("A different type item named '${step.name}' already exists, it will be deleted!")

        foundItem.delete()
        job = folder.createProject(jobDescriptor, step.name, true)
      }
      // the job exists
      else {
        job = foundItem as WorkflowJob
      }

      // update job definition if it's different
      if (!job.definition || (job.definition as CpsFlowDefinition).script != step.script) {
        job.definition = new CpsFlowDefinition(step.script, true)
      }
    }

    private ModifiableTopLevelItemGroup findOrCreateFolder() {
      Jenkins jenkins = Jenkins.get()
      TopLevelItemDescriptor folderDescriptor = jenkins.getDescriptor(Folder.class)

      ModifiableTopLevelItemGroup folder = jenkins

      if (step.folder) {
        String[] folderNames = step.folder.split('/')
        for (String folderName : folderNames) {
          Item next = folder.getItem(folderName)

          if (!next) {
            next = folder.createProject(folderDescriptor, folderName, true)
          } else if (!(next instanceof Folder)) {
            throw new Exception("${next.fullDisplayName} is not a folder!")
          }

          folder = next
        }
      }

      return folder
    }
  }

  @Extension
  static class Descriptor extends StepDescriptor {
    Set<? extends Class<?>> getRequiredContext() {
      [TaskListener.class] as Set
    }

    String getFunctionName() {
      'createJob'
    }

    boolean takesImplicitBlockArgument() {
      false
    }

    String getDisplayName() {
      "Create pipeline jobs"
    }

    String argumentsToString(Map<String, Object> namedArgs) {
      "Create job ${namedArgs.folder}/${namedArgs.name}"
    }
  }

  static class ScmConfig implements Serializable {
    private static final long serialVersionUID = 1L

    /**
     * Git organization
     */
    String organization = ''

    /**
     * Git repository
     */
    String repository = ''

    /**
     * Wildcard space separated git branch names to build and scan
     * for Pull Requests that target them
     */
    String branches = 'master'

    /**
     * Marker file that makes a branch eligible to be built
     */
    String markerFile = ''

    /**
     * Git jenkins credential id
     */
    String credentials

    /**
     * Branch periodic scan interval in minutes
     */
    String scanInterval

    /**
     * Label to use when notifying Pull Requests about build status
     */
    String prStatusLabel

    /**
     * Define if the Pull Request build status should be reported
     * to Github
     */
    Boolean prReportStatus = false

    /**
     * Define if Pull Requests are to be scanned
     */
    Boolean prScan = false

    /**
     * Define if the Pull Request should be merged with the target
     * branch before built
     */
    Boolean prMerge = true
  }
}
