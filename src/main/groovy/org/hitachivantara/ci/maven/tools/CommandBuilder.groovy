/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.maven.cli.CLIManager
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class CommandBuilder implements Serializable {
  private static final long serialVersionUID = 1L
  private static final String BASE_COMMAND = 'mvn'

  // Warning: CLIManager's constructor is not threadsafe, but the other methods are so we'll
  // just reuse the same instance to do our parsing.
  private static CLIManager cliManager = new CLIManager()

  List<String> goals = []
  List<String> options = []

  static CommandLine parse(String... args) {
    cliManager.parse(args)
  }

  @Whitelisted
  def leftShift(String cmd) {
    plus(cmd)
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  def plus(String cmd) {
    if (cmd) {
      CommandLine commandLine = parse(cmd.split())
      this.goals.addAll(commandLine.args)
      this.options.addAll(commandLine.options.collect { Option opt -> printOpt(opt) } as String[])
    }
    return this
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  def minus(String cmd) {
    if (cmd) {
      CommandLine commandLine = parse(cmd.split())
      this.goals.removeAll(commandLine.args)
      this.options.removeAll(commandLine.options.collect { Option opt -> printOpt(opt) } as String[])
    }
    return this
  }

  static String printOpt(Option opt) {
    def sb = '-' << opt.getOpt()
    if (opt.hasArg()) {
      sb << (opt.getOpt() == CLIManager.SET_SYSTEM_PROPERTY ? '' : ' ') << opt.value
    }
    return sb.toString()
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  boolean removeOption(String opt) {
    options.removeAll { it.startsWith(opt) }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  Map getUserProperties() {
    Map props = new HashMap()
    getOptionsValues(CLIManager.SET_SYSTEM_PROPERTY).each { String property ->
      String name, value
      int i = property.indexOf('=')
      if (i < 1) {
        name = property.trim()
        value = "true"
      } else {
        name = property.substring(0, i).trim()
        value = property.substring(i + 1).trim()
      }
      props.put(name, value)
    }
    return props
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getProjectList() {
    // per maven:
    // '!','-' are excluded projects
    // '+' are included projects
    getOptionsValues(CLIManager.PROJECT_LIST)
        .collectMany { it.split(',').toList() }
        .findAll { !it.startsWith('!') && !it.startsWith('-') }
        .collect { it.startsWith('+') ? it.substring(1) : it }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getExcludedProjectList() {
    // per maven:
    // '!','-' are excluded projects
    // '+' are included projects
    getOptionsValues(CLIManager.PROJECT_LIST)
        .collectMany { it.split(',').toList() }
        .findAll { it.startsWith('!') || it.startsWith('-') }
        .collect { it.substring(1) }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getActiveProfileIds() {
    // per maven:
    // '!','-' are inactive profiles
    // '+' are active profiles
    getOptionsValues(CLIManager.ACTIVATE_PROFILES)
        .collectMany { it.split(',').toList() }
        .findAll { !it.startsWith('!') && !it.startsWith('-') }
        .collect { it.startsWith('+') ? it.substring(1) : it }
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  List<String> getInactiveProfileIds() {
    // per maven:
    // '!','-' are inactive profiles
    // '+' are active profiles
    getOptionsValues(CLIManager.ACTIVATE_PROFILES)
        .collectMany { it.split(',').toList() }
        .findAll { it.startsWith('!') || it.startsWith('-') }
        .collect { it.substring(1) }
  }

  @Whitelisted
  List<String> getOptionsValues(def opt) {
    CommandLine commandLine = parse(options.join(' ').split())
    commandLine.getOptionValues(String.valueOf(opt))?.toList() ?: []
  }

  @Whitelisted
  Boolean hasOption(def opt) {
    CommandLine commandLine = parse(options.join(' ').split())
    commandLine.hasOption(String.valueOf(opt))
  }

  @SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
  @Whitelisted
  void validate() {
    goals.unique(true)
    options.unique(true)

    if (goals.empty) {
      throw new Exception("No goals have been specified for this build")
    }
    if (options.count { it.startsWith("-${CLIManager.ALTERNATE_POM_FILE} ") } > 1) {
      throw new Exception("Only one alternate POM file is allowed")
    }
  }

  @Whitelisted
  String build() {
    validate()
    StringBuilder sb = new StringBuilder()
    sb << BASE_COMMAND << ' '
    sb << goals.join(' ')
    if (options) sb << ' ' << options.join(' ')
    return sb
  }
}