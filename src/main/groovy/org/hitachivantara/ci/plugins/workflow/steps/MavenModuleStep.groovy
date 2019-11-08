/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import groovy.transform.CompileStatic
import hudson.Extension
import hudson.FilePath
import hudson.Util
import org.hitachivantara.ci.maven.tools.MavenModule
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

@CompileStatic
class MavenModuleStep extends Step implements Serializable {
  private static final long serialVersionUID = 1L

  private final String file
  private List<String> activeProfiles
  private List<String> inactiveProfiles
  private Properties userProperties

  @DataBoundConstructor
  MavenModuleStep(@Nonnull String file) {
    this.file = file
  }

  @Override
  StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(this, stepContext)
  }

  @DataBoundSetter
  void setActiveProfiles(@CheckForNull List<String> profiles) {
    this.activeProfiles = Util.fixNull(profiles)
  }

  @DataBoundSetter
  void setInactiveProfiles(@CheckForNull List<String> profiles) {
    this.inactiveProfiles = Util.fixNull(profiles)
  }

  @DataBoundSetter
  void setUserProperties(@CheckForNull Properties props) {
    this.userProperties = props ?: new Properties()
  }

  @Extension
  static class DescriptorImpl extends StepDescriptor {
    @Override
    Set<? extends Class<?>> getRequiredContext() {
      return Collections.singleton(FilePath.class)
    }

    @Override
    String getFunctionName() {
      return 'buildMavenModule'
    }

    @Override
    String getDisplayName() {
      return "${functionName} - Process a Maven Project file."
    }
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<MavenModule> {
    private static final long serialVersionUID = 2L

    private MavenModuleStep step

    protected Execution(@Nonnull MavenModuleStep step, @Nonnull StepContext context) {
      super(context)
      this.step = step
    }

    @Override
    protected MavenModule run() throws Exception {
      FilePath ws = getContext().get(FilePath.class)
      FilePath pom = ws.child(step.file)
      if (!pom.exists()) {
        throw new FileNotFoundException("${pom.remote} does not exist.")
      }
      if (pom.isDirectory()) {
        throw new FileNotFoundException("${pom.remote} is a directory.")
      }
      return MavenModule.builder()
        .withActiveProfiles(step.activeProfiles)
        .withInactiveProfiles(step.inactiveProfiles)
        .withUserProperties(step.userProperties)
        .build(pom)
    }
  }
}
