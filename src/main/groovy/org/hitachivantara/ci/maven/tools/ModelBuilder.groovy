/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import hudson.FilePath
import hudson.remoting.VirtualChannel
import org.apache.maven.model.Model
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingException
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.jenkinsci.remoting.RoleChecker

@Builder(builderStrategy = SimpleStrategy, prefix = 'with')
class ModelBuilder implements FilePath.FileCallable<Result>, Serializable {
  private static final long serialVersionUID = 1L

  List<String> activeProfiles
  List<String> inactiveProfiles
  Map userProperties

  Result build(FilePath pom) {
    pom.act(this)
  }

  @Override
  Result invoke(File pom, VirtualChannel channel) throws IOException, InterruptedException {
    org.apache.maven.model.building.ModelBuilder builder = new DefaultModelBuilderFactory().newInstance()
    ModelBuildingResult result

    Properties userPropertiesClass = new Properties();
    if(userProperties!=null){
      userPropertiesClass.putAll(userProperties);
    }

    try {
      DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
        .setPomFile(pom)
        .setModelResolver(new OfflineModelResolver())
        .setTwoPhaseBuilding(true)
        .setSystemProperties(System.properties)
        .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
        .setActiveProfileIds(activeProfiles)
        .setInactiveProfileIds(inactiveProfiles)
        .setUserProperties(userPropertiesClass)

      result = builder.build(request)
    } catch (ModelBuildingException e) {
      throw new IOException(e)
    }

    return new Result(
      rawModel: result.rawModel,
      effectiveModel: result.effectiveModel,
      modelIds: result.modelIds,
      rawModels: result.rawModels
    )
  }

  @Override
  void checkRoles(RoleChecker roleChecker) throws SecurityException {}

  static class Result implements Serializable {
    private static final long serialVersionUID = 1L

    Model effectiveModel
    Model rawModel
    List<String> modelIds
    Map<String, Model> rawModels
  }
}
