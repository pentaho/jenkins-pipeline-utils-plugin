/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import hudson.model.Label
import hudson.model.Result
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

class TemplateResolverStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()

  @Rule
  JenkinsRule jenkins = new JenkinsRule()

  @Shared
  private WorkflowJob job

  String template = 'My $who are ${do.what} this ${do.to} very closely, and I can promise that we shall ${do.will}.'
  String resolved = 'My officials are monitoring this situation very closely, and I can promise that we shall fix it.'

  String fileName = 'template-report.vm'

  void setup() {
    job = jenkins.createProject(WorkflowJob.class, "test-resolve-templates")
  }

  def "test velocity template resolver locally"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
          node {
            writeFile file: 'templates/${fileName}', text: '${template}'
          
            String noParams = resolveTemplate 'templates/${fileName}'
            echo 'unresolved: ' + noParams
            
            String withParams = resolveTemplate(
              file: 'templates/${fileName}',
              parameters: [
                who: 'officials',
                do: [what: 'monitoring', to: 'situation', will: 'fix it']
              ]
            )
            echo 'resolved: ' + withParams
          }
        """.stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)
      jenkins.assertLogContains('unresolved: ' + template, run)
      jenkins.assertLogContains('resolved: ' + resolved, run)
  }

  def "test velocity template resolver remote"() {
    setup:
      jenkins.createOnlineSlave(Label.get("slaves"))

      job.setDefinition(new CpsFlowDefinition("""\
          node('slaves') {
            writeFile file: 'templates/${fileName}', text: '${template}'
          
            String noParams = resolveTemplate 'templates/${fileName}'
            echo 'unresolved: ' + noParams
            
            String withParams = resolveTemplate(
              file: 'templates/${fileName}',
              parameters: [
                who: 'officials',
                do: [what: 'monitoring', to: 'situation', will: 'fix it']
              ]
            )
            echo 'resolved: ' + withParams
          }
        """.stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)
      jenkins.assertLogContains('unresolved: ' + template, run)
      jenkins.assertLogContains('resolved: ' + resolved, run)
  }

  def "test velocity template resolver with text"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
          node {
            String resolved = resolveTemplate(
              text: '${template}',
              parameters: [
                who: 'officials',
                do: [what: 'monitoring', to: 'situation', will: 'fix it']
              ]
            )
            echo 'resolved: ' + resolved
          }
        """.stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)
      jenkins.assertLogContains('resolved: ' + resolved, run)
  }

  def "test missing parameters"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
          node {
            resolveTemplate()
          }
        """.stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jenkins.assertBuildStatus(Result.FAILURE, build2)
  }
}
