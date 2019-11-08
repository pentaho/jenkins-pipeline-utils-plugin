/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import hudson.Extension
import hudson.FilePath
import hudson.model.Computer
import hudson.remoting.VirtualChannel
import org.hitachivantara.ci.io.FileDisposable
import org.jenkinsci.plugins.resourcedisposer.AsyncResourceDisposer
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.jenkinsci.remoting.RoleChecker
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class DeleteFilesStep extends Step implements Serializable {
  private static final long serialVersionUID = 1L

  private String path = ''
  private String regex = ''
  private boolean async = false

  @DataBoundConstructor
  DeleteFilesStep() {}

  @DataBoundSetter
  void setPath(String path) {
    this.path = path
  }

  @DataBoundSetter
  void setRegex(String regex) {
    this.regex = regex
  }

  @DataBoundSetter
  void setAsync(Boolean async) {
    this.async = async
  }

  StepExecution start(StepContext context) throws Exception {
    if (path || (!path && !regex)) {
      return new FileExecution(this, context)
    } else if (regex) {
      return new RegexExecution(this, context)
    }
  }

  static class FileExecution extends SynchronousNonBlockingStepExecution<Boolean> {
    private static final long serialVersionUID = 1L

    private DeleteFilesStep step

    FileExecution(@Nonnull DeleteFilesStep step, @Nonnull StepContext context) {
      super(context)
      this.step = step
    }

    protected Boolean run() throws Exception {
      FilePath file = context.get(FilePath.class).child(step.path)
      Computer computer = file.toComputer()

      if (!file.exists()) {
        return true
      }

      if (step.async && computer) {
        FilePath deleteMe = file.withSuffix("_delete_" + System.currentTimeMillis())
        file.renameTo(deleteMe)

        FileDisposable disposable = new FileDisposable(computer.name, deleteMe.remote)
        AsyncResourceDisposer.get().dispose(disposable)
      } else {
        file.deleteRecursive()
      }

      return true
    }
  }

  static class RegexExecution extends SynchronousNonBlockingStepExecution<Boolean> {
    private static final long serialVersionUID = 1L

    private DeleteFilesStep step

    RegexExecution(@Nonnull DeleteFilesStep step, @Nonnull StepContext context) {
      super(context)
      this.step = step
    }

    protected Boolean run() throws Exception {
      FilePath root = context.get(FilePath.class)

      if (!root.exists()) {
        return true
      }

      root.act(new DeleteVisitor(step.regex))
    }

    /**
     * Implement both FileVisitor to traverse file tree and FileCallable to run on the node where the files exist.
     */
    static class DeleteVisitor extends SimpleFileVisitor<Path> implements FilePath.FileCallable<Boolean> {
      private static final long serialVersionUID = 1L

      Pattern pattern

      DeleteVisitor(String regex) {
        this.pattern = Pattern.compile(regex)
      }

      FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (pattern.matcher(dir.fileName as String).matches()) {
          dir.toFile().deleteDir()
          return FileVisitResult.SKIP_SUBTREE
        }

        return FileVisitResult.CONTINUE
      }

      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (pattern.matcher(file.fileName as String).matches()) {
          file.toFile().delete()
        }

        return FileVisitResult.CONTINUE
      }

      Boolean invoke(File base, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        Files.walkFileTree(base.toPath(), this)
      }

      void checkRoles(RoleChecker roleChecker) throws SecurityException {}
    }
  }

  @Extension
  static class Descriptor extends StepDescriptor {
    Set<? extends Class<?>> getRequiredContext() {
      [FilePath.class] as Set
    }

    String getFunctionName() {
      'delete'
    }

    boolean takesImplicitBlockArgument() {
      false
    }

    String getDisplayName() {
      "Delete files"
    }

    String argumentsToString(Map<String, Object> namedArgs) {
      String argsString = 'Delete'

      if (namedArgs.path) {
        argsString += " ${namedArgs.path}"
      } else if (namedArgs.regex) {
        argsString += " ${namedArgs.regex}"
      }

      if(namedArgs.async){
        argsString += ' (async)'
      }

      return argsString
    }
  }

}
