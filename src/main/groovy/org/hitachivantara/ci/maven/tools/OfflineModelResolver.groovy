/*
 This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package org.hitachivantara.ci.maven.tools

import org.apache.maven.building.StringSource
import org.apache.maven.model.Dependency
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException

/**
 * Dumb model resolver that just returns a fake pom for the requested parent. This is used for external pom resolution
 * and we don't need to actually resolve it, at least for now.
 */
class OfflineModelResolver implements ModelResolver, Serializable {
  private static final long serialVersionUID = 1L
  OfflineModelResolver() {
  }

  ModelSource2 resolveModel(Dependency dependency) throws UnresolvableModelException {
    resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())
  }

  ModelSource2 resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
    StringSource ss = new StringSource("""<?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>${groupId}</groupId>
          <artifactId>${artifactId}</artifactId>
          <version>${version}</version>
          <packaging>pom</packaging>
        </project>
        """
    )
    return new ModelSource2() {
      @Override
      ModelSource2 getRelatedSource(String relPath) {
        return null
      }

      @Override
      URI getLocationURI() {
        return null
      }

      @Override
      InputStream getInputStream() throws IOException {
        return ss.getInputStream()
      }

      @Override
      String getLocation() {
        return ss.getLocation()
      }
    }
  }

  ModelSource2 resolveModel(Parent parent) throws UnresolvableModelException {
    resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion())
  }

  void addRepository(Repository repository) throws InvalidRepositoryException {
    /* NO-OP */
  }

  void addRepository(Repository repository, boolean b) throws InvalidRepositoryException {
    /* NO-OP */
  }

  ModelResolver newCopy() {
    new OfflineModelResolver()
  }
}