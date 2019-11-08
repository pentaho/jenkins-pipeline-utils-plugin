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

class MavenFilteredDependencyGraphStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()
  @Rule
  JenkinsRule jenkins = new JenkinsRule()
  @Shared
  private WorkflowJob job

  void setup() {
    jenkins.createOnlineSlave(Label.get("slaves"))
    job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline")
  }

  def "test scripted pipeline"() {
    given:
      URL resources = getClass().getResource('/inter-module-dependency-project/pom.xml')
      File pom = new File(resources.toURI())
    when:
      job.setDefinition(new CpsFlowDefinition(
          "def prepare() {\n" +
              "  def x = buildMavenModule file: '${pom.getPath()}'\n" +
              "  assert x != null\n" +
              "  echo \"allActiveModules: \${x.allActiveModules.toString()}\"\n" +
              "  return x\n" +
              "}\n" +
              "node('slaves') {\n" +
              "  def m = prepare()\n" +
              "  def x = projectDependencyGraph module: m\n" +
              "  assert x != null\n" +
              "  echo \"sortedGroups: \${x.sortedProjectsByGroups}\"\n" +
              "  def z = projectDependencyGraph module: m, whitelist: ['sub-1', 'sub-2', 'sub-3', 'sub-4']\n" +
              "  echo \"sortedGroups2: \${z.sortedProjectsByGroups}\"\n" +
              "}\n"
          , true))

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0))
      jenkins.assertLogContains("allActiveModules: [sub-5, sub-3, sub-2, sub-4, sub-1]", run)
      jenkins.assertLogContains("sortedGroups: [[sub-1, sub-4], [sub-2], [sub-5, sub-3]]", run)
      jenkins.assertLogContains("sortedGroups2: [[sub-1, sub-4], [sub-2], [sub-3]]", run)
  }
}
