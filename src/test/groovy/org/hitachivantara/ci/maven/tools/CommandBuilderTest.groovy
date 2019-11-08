/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import org.apache.commons.cli.CommandLine
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class CommandBuilderTest extends Specification {
  @Shared
  CommandBuilder builder

  void setup() {
    builder = new CommandBuilder()
  }

  def "test no goals throws exception"() {
    when:
      builder.build()
    then:
      thrown Exception
  }

  def "test many alternate pom file throws exception"() {
    when:
      builder << 'install -f pom.xml --file another.xml'
      builder.build()
    then:
      thrown Exception
  }

  def "test build() correctly parses arguments"() {
    given:
      builder << 'clean install'
      builder << '-DskipTests -B -e'
    expect:
      builder.build() == 'mvn clean install -DskipTests -B -e'
  }

  def "test goals and options are unique"() {
    given:
      builder << 'clean install'
      builder << 'clean -DskipTests'
      builder << '-Pbase -P base'
    expect:
      builder.build() == 'mvn clean install -DskipTests -P base'
  }

  def "test active profile ids extraction from command"() {
    given:
      builder << 'install'
      builder << options
    when:
      def profiles = builder.activeProfileIds
    then:
      profiles == expected

    where:
      options                    || expected
      '-Pbase,extra'             || ['base', 'extra']
      '-Pbase -P extra'          || ['base', 'extra']
      '-Pbase -P extra,extra2'   || ['base', 'extra', 'extra2']
      '--activate-profiles base' || ['base']
      '-P !assemblies'           || []
      '-P !assemblies,ui -Pbase' || ['ui', 'base']
  }

  def "test get inactive profiles"() {
    given:
      builder << 'install'
      builder << options
    when:
      def profiles = builder.inactiveProfileIds
    then:
      profiles == expected

    where:
      options                     || expected
      '-Pbase,extra'              || []
      '-P !assemblies'            || ['assemblies']
      '-P !assemblies,ui -P!base' || ['assemblies', 'base']
  }

  def "test project list extraction from command"() {
    given:
      builder << 'install'
      builder << options
    when:
      def projects = builder.projectList
    then:
      projects == expected

    where:
      options                  || expected
      '-pl core'               || ['core']
      '--projects core'        || ['core']
      '-pl core,ui'            || ['core', 'ui']
      '-pl core -pl ui'        || ['core', 'ui']
      '-pl core --projects ui' || ['core', 'ui']
  }

  def "test user properties extraction from command"() {
    setup:
      Properties props = new Properties()
      expected.each { k, v ->
        props.setProperty(k, v)
      }
    and:
      builder << 'install'
      builder << options
    when:
      def properties = builder.userProperties
    then:
      properties == props

    where:
      options               || expected
      '-Dproperty=1'        || ['property': '1']
      '--define property=1' || ['property': '1']
      '-Dopt'               || ['opt': 'true']
      '-Dopt=1 -Drelease'   || ['opt': '1', 'release': 'true']
  }

  def "test options removal"() {
    given:
      builder << 'clean install'
      builder << options
    when:
      builder.removeOption(subtract)
    then:
      builder.build() == expected

    where:
      options                   | subtract     || expected
      '-Dproperty=true'         | '-Dproperty' || 'mvn clean install'
      '-Dproperty -Drelease'    | '-Dproperty' || 'mvn clean install -Drelease'
      '-Dproperty -Drelease -B' | '-D'         || 'mvn clean install -B'
  }

  def "test incremental build"() {
    when:
      builder << 'clean package' << '-Daudit'
      builder -= 'package'
      builder += 'install'
    then:
      builder.build() == 'mvn clean install -Daudit'
  }

  def "test getters"() {
    given:
      builder << 'clean install -Daudit -Drelease'
    expect:
      builder.goals == ['clean', 'install']
      builder.options == ['-Daudit', '-Drelease']
  }

  def "test adding empty values"() {
    given:
      builder += param
    expect:
      builder.goals.isEmpty()
      builder.options.isEmpty()

    where:
      param << ['', null]
  }

  def "test subtracting empty values"() {
    given:
      builder -= param
    expect:
      builder.goals.isEmpty()
      builder.options.isEmpty()

    where:
      param << ['', null]
  }

  def "test getOptionsValue"() {
    given:
      builder += options
    expect:
      builder.getOptionsValues(opt) == expected
    where:
      options         | opt                 || expected
      '-f pom.xml'    | 'f'                 || ['pom.xml']
      '-B -e -Daudit' | 'D'                 || ['audit']
      '-Daudit=1'     | 'define'            || ['audit=1']
      '-B -e -Daudit' | 'B'                 || []
      '-P base,ui'    | 'P'                 || ['base,ui']
      '-P base -Pui'  | 'activate-profiles' || ['base', 'ui']
  }


  def "test concurrent maven command parse"() {
    when:
      CountDownLatch latch = new CountDownLatch(1)
      List results = []
      String expected = 'clean install -Daudit -Drelease -B -e'

      Closure string = { args, options ->
        args.join(' ') + ' ' + options.collect { CommandBuilder.printOpt(it) }.join(' ')
      }

      Closure parse = { ->
        latch.await()
        String result
        List tresults = []
        (1..100).each {
          try{
            CommandLine parsed = CommandBuilder.parse('clean', 'install', '-Daudit', '-Drelease', '-B', '-e')
            result = string(parsed.args, parsed.options)
          } catch(e){
            result = 'exception: ' + e.message
          }
          tresults.add(result)
        }
        synchronized (results){ results += tresults}
      }

      Thread t1 = new Thread(parse)
      Thread t2 = new Thread(parse)
      Thread t3 = new Thread(parse)

      t1.start()
      t2.start()
      t3.start()

      latch.countDown()
      t1.join()
      t2.join()
      t3.join()

      results.unique().remove(expected)

    then:
      results.isEmpty()
  }
}
