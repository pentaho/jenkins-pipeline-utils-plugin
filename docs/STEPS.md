# Implemented Steps

#### Maven Projects
* `getMavenCommandBuilder` - Creates a new [CommandBuilder](../src/main/groovy/org/hitachivantara/ci/maven/tools/CommandBuilder.groovy). ([help](../src/main/resources/org/hitachivantara/ci/plugins/workflow/steps/MavenCommandBuilderStep/help.html))
* `buildMavenModule` - Process a [Maven Project](https://maven.apache.org/pom.html) file into a [MavenModule](../src/main/groovy/org/hitachivantara/ci/maven/tools/MavenModule.groovy) data structure. ([help](../src/main/resources/org/hitachivantara/ci/plugins/workflow/steps/MavenModuleStep/help.html))
* `filterProjectGraph` - Provides a sub view of another dependency graph. Sorted and grouped by their interdependencies. ([help](../src/main/resources/org/hitachivantara/ci/plugins/workflow/steps/MavenProjectDependencyStep/help.html))

#### General Use
* `delete` - Deletes files and directories by path or regex. ([help](../src/main/resources/org/hitachivantara/ci/plugins/workflow/steps/DeleteFilesStep/help.html))
* `createJob` - Create pipeline Jobs. ([help](../src/main/resources/org/hitachivantara/ci/plugins/workflow/steps/CreateJobStep/help.html))
