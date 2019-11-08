/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.plugins.workflow.steps

import com.google.common.util.concurrent.FutureCallback
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.console.ConsoleLogFilter
import hudson.model.Run
import hudson.model.TaskListener
import hudson.remoting.RemoteOutputStream
import hudson.remoting.VirtualChannel
import jenkins.model.ArtifactManager
import jenkins.util.BuildListenerAdapter
import org.jenkinsci.remoting.util.PathUtils
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.StandardOpenOption
import jenkins.MasterToSlaveFileCallable
import org.apache.commons.io.output.TeeOutputStream
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback
import org.jenkinsci.plugins.workflow.steps.BodyInvoker
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.kohsuke.stapler.DataBoundConstructor

class LogFileStep extends Step {
  private final String file
  private Boolean archive
  private String archivePath
  private Boolean appendExisting

  @DataBoundConstructor
  LogFileStep(@Nonnull String file) {
    this.file = file
    this.archive = false
    this.archivePath = 'logs'
    this.appendExisting = false
  }

  @DataBoundSetter
  void setArchive(Boolean archive) {
    this.archive = archive
  }

  @DataBoundSetter
  void setArchivePath(String archivePath) {
    this.archivePath = archivePath
  }

  @DataBoundSetter
  void setAppendExisting(Boolean appendExisting) {
    this.appendExisting = appendExisting
  }
  
  StepExecution start(StepContext context) throws Exception {
    new Execution(context, this)
  }

  static class Execution extends StepExecution {
    private static final long serialVersionUID = 1L

    private transient LogFileStep step

    Execution(StepContext context, LogFileStep step) {
      super(context)
      this.step = step
    }

    boolean start() throws Exception {
      FilePath fp = context.get(FilePath.class).child(step.file)
      TeeFilter tee = new TeeFilter(fp, step.appendExisting)

      context.newBodyInvoker()
          .withContext(BodyInvoker.mergeConsoleLogFilters(context.get(ConsoleLogFilter.class), tee))
          .withCallback(BodyExecutionCallback.wrap(new Callback(this, step.file, step.archive, step.archivePath, tee)))
          .start()

      return false
    }
  }

  static class Callback implements FutureCallback, Serializable {
    private static final long serialVersionUID = 1L

    private Execution execution
    private String file
    private Boolean archive
    private String archivePath
    private TeeFilter tee

    Callback(Execution execution, String file, Boolean archive, String archivePath, TeeFilter tee){
      this.execution = execution
      this.file = file
      this.archive = archive
      this.archivePath = archivePath
      this.tee = tee
    }

    void onSuccess(Object o) {
      StepContext context = execution.context
      doArchive(context)
      context.onSuccess(o)
      close()
    }

    void onFailure(Throwable throwable) {
      StepContext context = execution.context
      doArchive(context)
      context.onFailure(throwable)
      close()
    }

    void doArchive(StepContext context) {
      if(archive) {
        FilePath workspace = context.get(FilePath.class)
        TaskListener listener = context.get(TaskListener.class)
        Launcher launcher = context.get(Launcher.class)
        Run run = context.get(Run.class)

        ArtifactManager artifactManager = run.pickArtifactManager()
        Map files = [(archivePath +'/' + file): file]
        artifactManager.archive(workspace, launcher, new BuildListenerAdapter(listener), files)
      }
    }

    void close() {
      tee.close()
    }
  }

  static class TeeFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L

    private final FilePath fp
    private transient OutputStream out

    TeeFilter(FilePath fp, Boolean appendExisting) {
      this.fp = fp

      if(fp.exists() && !appendExisting){
        fp.delete()
      }
    }

    OutputStream decorateLogger(Run build, final OutputStream logger) throws IOException, InterruptedException {
      out = new TeeOutputStream(logger, newOutputStream(fp))
      out
    }

    void close() {
      try {
        out?.close()
      } catch(IOException ignore) {}
    }
  }

  /**
   * Writes to this file.
   * If this file already exists, then bytes will be written
   * to the end of the file rather than the beginning.
   * If the directory doesn't exist, it will be created.
   *
   * @param fp
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  static OutputStream newOutputStream(FilePath fp) throws IOException, InterruptedException {
    if (fp.isRemote()) {
      // remote file
      return fp.act(new MasterToSlaveFileCallable<OutputStream>() {
        private static final long serialVersionUID = 1L
        OutputStream invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
          f = f.absoluteFile
          mkdirs(f.parentFile)
          if (!f.parentFile.exists()) {
            throw new IOException("Failed to create directories ${f}")
          }
          try {
            return new RemoteOutputStream(Files.newOutputStream(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND))
          } catch (InvalidPathException e) {
            throw new IOException(e)
          }
        }
      })
    } else {
      // local file
      File f = new File(fp.remote).absoluteFile
      mkdirs(f.parentFile)
      if (!f.parentFile.exists()) {
        throw new IOException("Failed to create directories ${f}")
      }
      try {
        return Files.newOutputStream(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      } catch (InvalidPathException e) {
        throw new IOException(e)
      }
    }
  }

  static boolean mkdirs(File dir) throws IOException {
    if (dir.exists()) {
      return false
    }

    Files.createDirectories(PathUtils.fileToPath(dir))
    return true
  }

  @Extension
  static class Descriptor extends StepDescriptor {
    Set<? extends Class<?>> getRequiredContext() {
      [FilePath.class, Run.class, Launcher.class, TaskListener.class] as Set
    }

    String getFunctionName() {
      'logfile'
    }

    boolean takesImplicitBlockArgument() {
      true
    }

    String getDisplayName() {
      'Saves logged output to file and optionally archives it'
    }
  }

}