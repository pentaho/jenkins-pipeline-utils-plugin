/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.CompileStatic
import hudson.Extension
import hudson.Util
import org.hitachivantara.ci.maven.tools.FilteredProjectDependencyGraph
import org.hitachivantara.ci.maven.tools.MavenModule
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

@CompileStatic
class MavenFilteredDependencyGraphStep extends Step {
  @Nonnull
  private final MavenModule module
  private List<String> whitelist

  @DataBoundConstructor
  MavenFilteredDependencyGraphStep(@Nonnull MavenModule module) {
    this.module = module
  }

  @DataBoundSetter
  void setWhitelist(@CheckForNull List<String> whitelist) {
    this.whitelist = Util.fixNull(whitelist)
  }

  @Override
  StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(this, stepContext)
  }

  @Extension
  static class DescriptorImpl extends StepDescriptor {
    @Override
    Set<? extends Class<?>> getRequiredContext() {
      return Collections.emptySet()
    }

    @Override
    String getFunctionName() {
      return 'projectDependencyGraph'
    }

    @Override
    String getDisplayName() {
      return "${functionName} - Provides a sub view of another dependency graph."
    }
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<FilteredProjectDependencyGraph> {
    private static final long serialVersionUID = 1L

    @SuppressFBWarnings( value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting." )
    private transient MavenFilteredDependencyGraphStep step

    protected Execution(@Nonnull MavenFilteredDependencyGraphStep step, @Nonnull StepContext context) {
      super(context)
      this.step = step
    }

    @Override
    protected FilteredProjectDependencyGraph run() throws Exception {
      return new FilteredProjectDependencyGraph(step.module, step.whitelist)
    }
  }
}
