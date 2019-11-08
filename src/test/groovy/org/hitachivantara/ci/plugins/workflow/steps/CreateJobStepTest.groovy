/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/


package org.hitachivantara.ci.plugins.workflow.steps

import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger
import net.gleske.scmfilter.impl.trait.WildcardSCMHeadFilterTrait
import org.hitachivantara.ci.scm.DisableGithubStatusUpdateTrait
import org.hitachivantara.ci.scm.CustomGithubStatusLabelTrait
import org.jenkinsci.plugins.github_branch_source.BranchDiscoveryTrait
import org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.plugins.inlinepipeline.InlineDefinitionBranchProjectFactory
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.junit.ClassRule
import org.junit.Rule
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Future

class CreateJobStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()
  @Rule
  JenkinsRule jRule = new JenkinsRule()
  @Shared
  private WorkflowJob job

  void setup() {
    job = jRule.createProject(WorkflowJob.class, "test-createJob")
  }

  def "test create an empty pipeline job"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
        node {
          createJob 'myJob'
        }
      """.stripIndent(), true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)
      jRule.getInstance().getItemByFullName('myJob')
  }

  def "test create a pipeline job"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""
        node {
          createJob(
            name: 'myJob', 
            folder: 'the/job/folder', 
            script: "echo 'something'"
          )
        }
      """.stripIndent(), true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      WorkflowJob createdJob = jRule.getInstance().getItemByFullName('the/job/folder/myJob')
      createdJob != null
      createdJob.definition.script == "echo 'something'"
  }

  def "test create an empty multibranch pipeline job"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""
        node {
          createJob(
            name: 'myJob', 
            multibranch: true
          )
        }
      """.stripIndent(), true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)
      jRule.getInstance().getItemByFullName('myJob')
  }

  def "test create a multibranch pipeline job"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""
        node {
          createJob(
            name: 'myJob', 
            multibranch: true,
            scmConfig: [
              organization: 'org',
              repository: 'repo',
              branches: 'master',
              scanInterval: 10
            ]
          )
        }
      """.stripIndent(), true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      WorkflowMultiBranchProject createdJob = jRule.getInstance().getItemByFullName('myJob')
      createdJob != null

      GitHubSCMSource scmSource = createdJob.getSCMSources().first()
      scmSource.repoOwner == 'org'
      scmSource.repository == 'repo'

      scmSource.traits.size() == 3
      scmSource.traits.any { it instanceof BranchDiscoveryTrait }
      scmSource.traits.any { it instanceof WildcardSCMHeadFilterTrait }
      scmSource.traits.any { it instanceof DisableGithubStatusUpdateTrait }

      PeriodicFolderTrigger trigger = createdJob.triggers.values().first()
      trigger != null
      trigger.interval == '10m'
  }

  def "test create a multibranch pipeline job w/ PR scan and report"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""
        node {
          createJob(
            name: 'myJob', 
            multibranch: true,
            script: "echo 'something'",
            scmConfig: [
              markerFile: '.build-me',
              prScan: true,
              prStatusLabel: 'Bob the Builder',
              prReportStatus: true
            ]
          )
        }
      """.stripIndent(), true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      WorkflowMultiBranchProject createdJob = jRule.getInstance().getItemByFullName('myJob')
      createdJob != null

      GitHubSCMSource scmSource = createdJob.getSCMSources().first()

      scmSource.traits.size() == 4
      scmSource.traits.any { it instanceof BranchDiscoveryTrait }
      scmSource.traits.any { it instanceof WildcardSCMHeadFilterTrait }
      scmSource.traits.any { it instanceof ForkPullRequestDiscoveryTrait }
      scmSource.traits.any { it instanceof CustomGithubStatusLabelTrait }

      CustomGithubStatusLabelTrait notifTrait = scmSource.traits.find { it instanceof CustomGithubStatusLabelTrait }
      notifTrait.label == 'Bob the Builder'

      InlineDefinitionBranchProjectFactory factory = createdJob.projectFactory
      factory.script == "echo 'something'"
      factory.markerFile == '.build-me'
  }

}
