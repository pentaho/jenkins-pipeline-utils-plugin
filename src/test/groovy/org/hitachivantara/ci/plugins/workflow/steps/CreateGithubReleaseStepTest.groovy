/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.CredentialsStore
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.ClassRule
import org.junit.Rule
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Future

class CreateGithubReleaseStepTest extends Specification {
  @ClassRule
  static BuildWatcher buildWatcher = new BuildWatcher()

  @Rule
  JenkinsRule jenkins = new JenkinsRule()

  @Shared
  private WorkflowJob job

  void setup() {
    job = jenkins.createProject(WorkflowJob.class, "test-create-github-release")
  }

  def "test create github release"() {
    setup:

    String credentialsId = 'jenkinsCredentialsId'
    CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next()
    // correct credentials
    store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
      CredentialsScope.GLOBAL, credentialsId, 'description', 'foo', 'bar'
    ))
    // some other credentials
    store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
      CredentialsScope.GLOBAL, 'some-other-creds-id', 'description', 'fooo', 'baar'
    ))

    GHRepository repository = Mock(GHRepository) {
      listReleases() >> {
        Mock(PagedIterable) {
          asList() >> []
        }
      }
    }

    GHReleaseBuilder releaseBuilder = GroovySpy(GHReleaseBuilder, global: true, constructorArgs:[repository, '']) {
      create() >> Mock(GHRelease)
    }
    repository.createRelease(_) >> releaseBuilder

    releaseBuilder.name(_) >> { String name ->
      return releaseBuilder
    }
    releaseBuilder.body(_) >> { String body ->
      return releaseBuilder
    }

    GroovySpy(GitHub, global: true, constructorArgs:['', null, null, null, null, null, null]) {
      getRepository(_) >> repository
      asBoolean() >> true
    }

    job.setDefinition(new CpsFlowDefinition("""\
          node {
            Boolean saved = createGithubRelease(
              credentials: '${credentialsId}',
              repository: 'owner/a-repository',
              name: 'release-with-a-name',
              text: 'release-text',
              update: false 
            )
          }
        """.stripIndent()
      , true))

    when:
    Future build2 = job.scheduleBuild2(0)

    then: 'the build was successful'
    WorkflowRun run = jenkins.assertBuildStatusSuccess(build2)

    and: 'the parameters were passed accordingly'
    jenkins.assertLogContains("Parameters=[" +
      "Repository: owner/a-repository, " +
      "CredentialsId: ${credentialsId}, " +
      "Name: release-with-a-name, " +
      "Update: false, " +
      "Text: release-text]", run)

    and: 'the right credentials were picked up'
    jenkins.assertLogContains("Retrieving credentials with credentialsId '${credentialsId}'", run)

  }
}
