/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.io

import hudson.FilePath
import hudson.model.Computer
import jenkins.model.Jenkins
import org.jenkinsci.plugins.resourcedisposer.Disposable

class FileDisposable implements Disposable {
  private static final long serialVersionUID = 1L

  String node
  String path

  FileDisposable(String node, String path) {
    this.node = node
    this.path = path
  }

  State dispose() throws Throwable {
    Jenkins jenkins = Jenkins.get()
    if (!jenkins) {
      return State.TO_DISPOSE
    } else {
      Computer computer = jenkins.getComputer(node)
      if (!computer) {
        return State.PURGED
      } else {
        FilePath file = new FilePath(computer.channel, path)

        try {
          file.deleteRecursive()
        } catch (IOException e) {
          Throwable cause = e.getCause()
          if (cause != null && e.getMessage().startsWith("remote file operation failed:")) {
            throw cause
          }

          throw e
        }

        return file.exists() ? State.TO_DISPOSE : State.PURGED
      }
    }
  }

  String getDisplayName() {
    "File '$path' on ${node ?: 'master'}"
  }
}
