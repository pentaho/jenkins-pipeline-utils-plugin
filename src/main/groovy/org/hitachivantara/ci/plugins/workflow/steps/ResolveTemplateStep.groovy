/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.Extension
import hudson.FilePath
import hudson.remoting.VirtualChannel
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.jenkinsci.remoting.RoleChecker
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

class ResolveTemplateStep extends Step {

  String text
  String file
  String encoding = 'UTF-8'
  Map parameters = [:]

  @DataBoundConstructor
  ResolveTemplateStep(@Nonnull String file) {
    this.file = file
  }

  @DataBoundSetter
  void setParameters(@Nonnull Map parameters) {
    this.parameters = parameters
  }

  @DataBoundSetter
  void setEncoding(@Nonnull String encoding) {
    this.encoding = encoding
  }

  @DataBoundSetter
  void setText(@Nonnull String text) {
    this.text = text
  }

  @Override
  StepExecution start(StepContext stepContext) throws Exception {
    new Execution(stepContext, this)
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  private static String resolve(String template, Map parameters) {
    StringWriter writer = new StringWriter()

    VelocityContext context = new VelocityContext()
    parameters.each { key, value ->
      context.put(key, value)
    }

    Velocity.init()
    Velocity.evaluate(context, writer, 'resolveTemplate', template)

    return writer.toString()
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<String> {
    private static final long serialVersionUID = 1L

    transient ResolveTemplateStep step

    Execution(StepContext context, ResolveTemplateStep step) {
      super(context)
      this.step = step
    }

    @Override
    protected String run() throws Exception {
      if (step.file) {
        FilePath template = context.get(FilePath.class).child(step.file)

        if (!template.exists()) {
          throw new FileNotFoundException("Could not find template '${template.remote}")
        }

        String text = template.act(new ReadTemplateFileCallable(step.encoding))
        return resolve(text, step.parameters)
      } else if (step.text) {
        return resolve(step.text, step.parameters)
      } else {
        throw new IllegalArgumentException("Either a file or a text template needs to be defined.")
      }
    }
  }

  static class ReadTemplateFileCallable implements FilePath.FileCallable<String> {
    private static final long serialVersionUID = 1L

    String encoding

    ReadTemplateFileCallable(String encoding) {
      this.encoding = encoding
    }

    @Override
    String invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
      file.getText(encoding)
    }

    @Override
    void checkRoles(RoleChecker roleChecker) throws SecurityException {}
  }

  @Extension
  static class Descriptor extends StepDescriptor {

    @Override
    Set<? extends Class<?>> getRequiredContext() {
      [FilePath.class] as Set
    }

    @Override
    String getFunctionName() {
      'resolveTemplate'
    }

    @Override
    boolean takesImplicitBlockArgument() {
      false
    }

    @Override
    String getDisplayName() {
      'Parse a Velocity template'
    }

    @Override
    String argumentsToString(Map<String, Object> namedArgs) {
      namedArgs.file ?: "Text template"
    }
  }
}
