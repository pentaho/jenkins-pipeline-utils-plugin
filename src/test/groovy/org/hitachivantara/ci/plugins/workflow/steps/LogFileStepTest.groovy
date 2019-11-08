/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/


package org.hitachivantara.ci.plugins.workflow.steps

import hudson.model.Label
import hudson.model.Result
import hudson.model.Run
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.ClassRule
import org.junit.Rule
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Future

class LogFileStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()
  @Rule
  JenkinsRule jenkins = new JenkinsRule()
  @Shared
  private WorkflowJob job

  void setup() {
    jenkins.createOnlineSlave(Label.get("slaves"))
    job = jenkins.createProject(WorkflowJob.class, "test-logfile")
  }

  def "test logfile usage"() {
    setup:
      job.setDefinition(new CpsFlowDefinition(
        "node('slaves') {\n" +
            "  logfile(file: 'the_log_file.log', archive: true){\n" +
            "     echo 'this is a log message'\n" +
            "     echo 'this is another log message'\n" +
            "  }\n" +
            "}\n"
        , true))
    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)
      jenkins.assertLogContains("this is a log message", run)
      jenkins.assertLogContains("this is another log message", run)

      List artifacts = job.getBuildByNumber(1).artifacts

      artifacts.size() == 1
      Run.Artifact artifact = artifacts.first()
      artifact.relativePath == 'logs/the_log_file.log'

      artifact.file.text.contains('this is a log message')
      artifact.file.text.contains('this is another log message')
  }

  def "test logfile is archived when it catches a error"() {
    setup:
      job.setDefinition(new CpsFlowDefinition(
          "node('slaves') {\n" +
              "  logfile(file: 'the_log_file.log', archive: true){\n" +
              "     echo 'this is a log message'\n" +
              "     error 'ops'\n" +
              "  }\n" +
              "}\n"
          , true))
    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE ,build2)
      jenkins.assertLogContains("this is a log message", run)

      List artifacts = job.getBuildByNumber(1).artifacts

      artifacts.size() == 1
      Run.Artifact artifact = artifacts.first()
      artifact.relativePath == 'logs/the_log_file.log'

      artifact.file.text.contains('this is a log message')
  }

  def "test appending of logfile"() {
    setup:
      job.setDefinition(new CpsFlowDefinition(
        """node('slaves') { 
            def file = 'the_log_file.log' 
            dir('logs') {
              logfile(file: file, archive: true){ 
                echo 'log message number 1'
              }
            }
            dir('logs') {
              logfile(file: file, archive: true, appendExisting: ${appendToExistingFile}){ 
                echo 'log message number 2'
              }
            }
          }""".stripIndent()
        , true))
    when:
      Future build2 = job.scheduleBuild2(0)

      then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)
      messages.each {
        jenkins.assertLogContains(it, run)
      }

      List artifacts = job.getBuildByNumber(1).artifacts

      artifacts.size() == 1
      Run.Artifact artifact = artifacts.first()

      messages.each {
        artifact.file.text.contains(it)
      }

    where:
      appendToExistingFile || messages
      true                 || ['log message number 1', 'log message number 2']
      false                || ['log message number 2']

  }

}
