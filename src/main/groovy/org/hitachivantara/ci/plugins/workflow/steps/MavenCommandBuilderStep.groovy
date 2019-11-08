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
import org.hitachivantara.ci.maven.tools.CommandBuilder
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
class MavenCommandBuilderStep extends Step {
  private String options

  @DataBoundConstructor
  MavenCommandBuilderStep() { }

  @Override
  StepExecution start(StepContext stepContext ) throws Exception {
    return new Execution( this, stepContext )
  }

  @DataBoundSetter
  void setOptions(@CheckForNull String options ) {
    this.options = Util.fixNull( options )
  }

  String getOptions() {
    return options
  }

  @Extension
  static class DescriptorImpl extends StepDescriptor {
    @Override
    Set<? extends Class<?>> getRequiredContext() {
      return Collections.emptySet()
    }

    @Override
    String getFunctionName() {
      return "getMavenCommandBuilder"
    }

    @Override
    String getDisplayName() {
      return "${functionName} - Parse a Maven command line."
    }
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<CommandBuilder> {
    @SuppressFBWarnings( value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting." )
    private transient MavenCommandBuilderStep step

    Execution( @Nonnull MavenCommandBuilderStep step, @Nonnull StepContext context ) {
      super( context )
      this.step = step
    }

    @Override protected CommandBuilder run() throws Exception {
      CommandBuilder command = new CommandBuilder()
      if (step.options) command += step.options
      return command
    }

    private static final long serialVersionUID = 1L
  }
}
