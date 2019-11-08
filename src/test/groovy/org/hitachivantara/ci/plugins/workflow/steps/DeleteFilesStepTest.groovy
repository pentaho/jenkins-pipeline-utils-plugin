/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/


package org.hitachivantara.ci.plugins.workflow.steps

import hudson.model.Label
import hudson.model.Slave
import org.jenkinsci.plugins.resourcedisposer.AsyncResourceDisposer
import org.jenkinsci.plugins.resourcedisposer.Disposable
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.junit.ClassRule
import org.junit.Rule
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Future

class DeleteFilesStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()
  @Rule
  JenkinsRule jRule = new JenkinsRule()
  @Shared
  private WorkflowJob job
  @Shared
  private Slave slave

  void setup() {
    slave = jRule.createOnlineSlave(Label.get("slaves"))
    job = jRule.createProject(WorkflowJob.class, "test-deleteFile")
  }

  def "test delete file"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          writeFile file: 'dir/f1', text: 'some text'
          writeFile file: 'dir/f2', text: 'some text'
          writeFile file: 'dir/sub-dir/f3', text: 'some text'
          
          delete(path: 'dir')
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      !new File(workspace, 'dir').exists()
  }

  def "test no arguments deletes current directory"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          writeFile file: 'aDir/otherDir/aFile', text: 'some text'
          
          dir('aDir/otherDir'){
            delete()
          }
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      new File(workspace, 'aDir').exists()
      !new File(workspace, 'aDir/otherDir').exists()
  }

  def "test delete subfolder by regex"() {
    setup:
      String regex = '.*sub.*'

      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          writeFile file: 'dir/f1.txt', text: 'some text'
          writeFile file: 'dir/f2.txt', text: 'some text'
          writeFile file: 'dir/sub-dir/f3.txt', text: 'some text'
          
          delete(regex: '$regex')
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      new File(workspace, 'dir').exists()
      !new File(workspace, 'dir/sub-dir').exists()
  }

  def "test delete files by regex"() {
    setup:
      String regex = '.*\\\\.log'

      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          writeFile file: 'dir/f1.txt', text: 'some text'
          writeFile file: 'dir/f2.log', text: 'some text'
          writeFile file: 'dir/sub-dir/f3.log', text: 'some text'
          
          delete(regex: '$regex')
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      new File(workspace, 'dir/f1.txt').exists()
      !new File(workspace, 'dir/f2.log').exists()
      !new File(workspace, 'dir/sub-dir/f3.log').exists()
  }

  def "test delete non existing file"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          delete(path: 'dir')
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      !new File(workspace, 'dir').exists()
  }

  def "test delete non existing file async"() {
    setup:
      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          delete(path: 'dir', async: true)
          
          dir('non-existing-dir'){
            delete(regex: '.*')  
          }
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)

      File workspace = new File(slave.getWorkspaceFor(job).remote)
      !new File(workspace, 'dir').exists()
  }

  def "test delete file async"() {
    setup:
      GroovySpy(AsyncResourceDisposer, global: true) {
        get() >> new AsyncResourceDisposer()
        dispose(_) >> { Disposable d -> d.dispose() }
        asBoolean() >> true
      }

      job.setDefinition(new CpsFlowDefinition("""\
        node('slaves') {
          writeFile file: 'dir/f1.txt', text: 'some text'
          
          delete(path: 'dir', async: true)
        }""".stripIndent()
        , true))

    when:
      Future build2 = job.scheduleBuild2(0)

    then:
      jRule.assertBuildStatusSuccess(build2)


      File workspace = new File(slave.getWorkspaceFor(job).remote)
      !workspace.list()
  }

}
