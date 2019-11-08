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

class MavenModuleStepTest extends Specification {
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
      URL resources = getClass().getResource('/multi-module-profiled-project/pom.xml')
      File pom = new File(resources.toURI())
    when:
      job.setDefinition(new CpsFlowDefinition("""\
          import org.hitachivantara.ci.maven.tools.MavenModule
          
          def doBuild() {
            MavenModule x = buildMavenModule file: '${pom.getPath()}'

            assert x != null
            assert x.id == 'com.pentaho.example:multimodule-parent'
            
            echo \"allActiveModules: \${x.allActiveModules.toString()}\"
            echo \"activeModules: \${x.activeModules.toString()}\"
          }
          
          def doCmdBuild() {
            def cmd = getMavenCommandBuilder() 
            cmd << 'install -P profile-A -DskipDefault' 
            cmd.validate()
            
            def x = buildMavenModule file: '${pom.getPath()}', activeProfiles: cmd.activeProfileIds, userProperties: cmd.userProperties
            assert x != null
            echo \"allActiveModules2: \${x.allActiveModules.toString()}\"
            echo \"activeModules2: \${x.activeModules.toString()}\"
            
            x = buildMavenModule file: '${pom.getPath()}', activeProfiles: ['profile-B'], userProperties: cmd.userProperties
            echo \"allActiveModules3: \${x.allActiveModules.toString()}\"
            
          }
          
          def doChildBuild() {
            MavenModule x = buildMavenModule file: '${new File(pom.parentFile, 'sub-2/pom.xml').getPath()}'
            assert x != null
            echo \"allActiveModules4: \${x.allActiveModules.toString()}\"
          }
          
          node('slaves') {
            doBuild()
            doCmdBuild()
            doChildBuild()
          }
          """.stripIndent()
          , true))

    then:
      WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0))
      jenkins.assertLogContains("allActiveModules: [sub-1, sub-2, sub-3, sub-2/sub-1, sub-2/subsub-2, sub-2/sub-23, sub-3/sub-1, sub-3/sub-1/sub-11]", run)
      jenkins.assertLogContains("allActiveModules2: [sub-1, sub-2, sub-2/sub-1, sub-2/subsub-2, sub-2/sub-23]", run)
      jenkins.assertLogContains("allActiveModules3: [sub-3, sub-3/sub-1, sub-3/sub-1/sub-11]", run)
      jenkins.assertLogContains("allActiveModules4: [sub-1, subsub-2, sub-23]", run)
      jenkins.assertLogContains("activeModules: [sub-1, sub-2, sub-3]", run)
      jenkins.assertLogContains("activeModules2: [sub-1, sub-2]", run)
  }
}
