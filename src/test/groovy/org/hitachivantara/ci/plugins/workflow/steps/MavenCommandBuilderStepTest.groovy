/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import hudson.model.Label
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.ClassRule
import org.junit.Rule
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Shared
import spock.lang.Specification

class MavenCommandBuilderStepTest extends Specification {
  @ClassRule static BuildWatcher buildWatcher = new BuildWatcher()
  @Rule JenkinsRule jenkins = new JenkinsRule()
  @Shared private WorkflowJob job

  void setup() {
    jenkins.createOnlineSlave(Label.get("slaves"))
    job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline")
  }

  def "test scripted pipeline"() {
    setup:
      def script = '' << ''
      script << 'node("slaves") {' << '\n'
      script << '  def cmd = getMavenCommandBuilder()' << '\n'
      script << '  cmd << "clean install"' << '\n'
      script << '  echo "test1: ${cmd.build()}"' << '\n'
      script << '  cmd = getMavenCommandBuilder(options: "-f pom.xml clean install")' << '\n'
      script << '  echo "test2: ${cmd.build()}"' << '\n'
      script << '}'

    when:
      job.setDefinition(new CpsFlowDefinition(script.toString(), true))

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0))
      jenkins.assertLogContains("test1: mvn clean install", run)
      jenkins.assertLogContains("mvn clean install -f pom.xml", run)
  }
}
