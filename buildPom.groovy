#!/usr/bin/env groovy
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.xml.XmlParser
import groovy.xml.XmlUtil
import org.apache.commons.cli.Option

@EqualsAndHashCode
@ToString
class Dependency {
    String group, artifact, version
}

class PomBuilder {
    def IGNORABLE_ARTIFACTS = ["spring-cloud-stream-binder-kafka-test-support",
                               "spring-boot-test-support",
                               "spring-boot-security-tests",
                               "spring-boot-configuration-docs",
                               "spring-boot-project",
                               "spring-boot-cli",
                               "gradle-language-java",
                               "gradle-language-jvm",
                               "gradle-platform-jvm",
                               "gradle-plugins",
                               "micrometer-registry-stackdriver",
                               "kotlinx-coroutines-core-native",
                               "kotlinx-coroutines-play-services"]

    static def IGNORABLE_GROUPS = ["org.elasticsearch.distribution.integ-test-zip"]

    static def KNOWN_DODGY_DEPENDENCIES = [
            'kotlin-': 'org.jetbrains.kotlin',
            'zipkin-': 'io.zipkin.reporter2',
            'brave'  : 'io.zipkin.brave'
    ]

    static List<Closure<Dependency>> DEPENDENCY_BLACKLIST = [
            blacklistArtifacts(
                    'kotlin-annotation-processing',
                    'kotlin-stdlib-wasm'
            ),
            { it.artifact.startsWith('kotlinx-coroutines-core-') && it.artifact != 'kotlinx-coroutines-core-js' }
    ]

    static Closure<Dependency> blacklistArtifacts(String... names) {
        { Dependency dep -> names.any { it == dep.artifact } }
    }

    static def allRepos = [
            [id: 'central', url: 'https://repo1.maven.org/maven2/'],
            [id: 'gradle-plugins', url: 'https://plugins.gradle.org/m2/'],
            [id: 'jcenter', url: 'https://jcenter.bintray.com/'],
            [id: 'imagej-public', url: 'https://maven.scijava.org/content/repositories/public/'],
            [id: 'gradle-releases', url: 'https://repo.gradle.org/gradle/libs-releases-local/'],
            [id: 'gemstone-release-cache', url: 'https://repo.spring.io/gemstone-release-cache/'],
            [id: 'gemstone-release-pivotal-cache', url: 'https://repo.spring.io/gemstone-release-pivotal-cache/'],
            [id: 'mulesoft', url: 'https://repository.mulesoft.org/nexus/content/repositories/public/'],
            [id: 'icm', url: 'http://maven.icm.edu.pl/artifactory/repo/'],
            [id: 'google', url: 'https://maven.google.com/'],
            [id: 'kotlin-plugin', url: 'https://dl.bintray.com/kotlin/kotlin-plugin/']
    ]

    def outputPom
    def pomCache = [:]
    def addedDependencies = [] as Set
    def addedRepos = [] as Set
    def outputName
    def repos

    PomBuilder(outputName, enabledRepoIds) {
        this.outputName = outputName
        this.repos = enabledRepoIds.collect { id -> allRepos.find { it.id == id } } ?: allRepos
        this.outputPom = pomFromTemplate(outputName, repos)
    }

    // bare bones POM XML which downloads all the stuff we need to the right place
    def pomFromTemplate(outputName, repos) {
        def pomRepos = repos.collect {
            """\
      <repository>
        <id>${it.id}</id>
        <url>${it.url}</url>
      </repository>""".stripIndent()
        }.join('\n')

        // base template for output pom
        def pomTemplate = """\
      <project xmlns="http://maven.apache.org/POM/4.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <!-- The Basics -->
        <groupId>group</groupId>
        <artifactId>artifact</artifactId>
        <version>1</version>
      <repositories>
      $pomRepos
      </repositories>
        <build>
          <plugins>
            <plugin>
              <artifactId>maven-resources-plugin</artifactId>
              <version>2.3</version>
              <executions>
                <execution>
                  <id>default-resources</id>
                  <phase>none</phase>
                </execution>
                <execution>
                  <id>default-testResources</id>
                  <phase>none</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <artifactId>maven-compiler-plugin</artifactId>
              <version>2.0.2</version>
              <executions>
                <execution>
                  <id>default-compile</id>
                  <phase>none</phase>
                </execution>
                <execution>
                  <id>default-testCompile</id>
                  <phase>none</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <artifactId>maven-surefire-plugin</artifactId>
              <version>2.10</version>
              <executions>
                <execution>
                  <id>default-test</id>
                  <phase>none</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <artifactId>maven-jar-plugin</artifactId>
              <version>2.2</version>
              <executions>
                <execution>
                  <id>default-jar</id>
                  <phase>none</phase>
                </execution>
              </executions>
            </plugin>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-dependency-plugin</artifactId>
              <version>2.10</version>
              <configuration>
                <outputDirectory>$outputName</outputDirectory>
                <useRepositoryLayout>true</useRepositoryLayout>
              </configuration>
              <executions>
                <execution>
                  <id>jars</id>
                  <phase>package</phase>
                  <goals>
                    <goal>copy-dependencies</goal>
                  </goals>
                  <configuration>
                    <addParentPoms>true</addParentPoms>
                    <copyPom>true</copyPom>
                  </configuration>
                </execution>
                <execution>
                  <id>docs</id>
                  <phase>package</phase>
                  <goals>
                    <goal>copy-dependencies</goal>
                  </goals>
                  <configuration>
                    <classifier>javadoc</classifier>
                  </configuration>
                </execution>
                <execution>
                  <id>sources</id>
                  <phase>package</phase>
                  <goals>
                    <goal>copy-dependencies</goal>
                  </goals>
                  <configuration>
                    <classifier>sources</classifier>
                  </configuration>
                </execution>
              </executions>
            </plugin>
          </plugins>
          <extensions>
              <extension>
                  <groupId>kr.motd.maven</groupId>
                  <artifactId>os-maven-plugin</artifactId>
                  <version>1.2.3.Final</version>
              </extension>
          </extensions>
        </build>
      <dependencies>
      </dependencies>
      </project>""".stripIndent()

        new XmlParser().parseText(pomTemplate)
    }

    // add all the dependencies we need to the POM object model and then dump the POM to file
    def generatePom(artifacts, groups, outputDirectory) {

        def dependencies = artifacts.collect { it ->
            def gav = it.tokenize(':')
            new Dependency(group: gav[0], artifact: gav[1], version: gav[2])
        }

        dependencies.addAll(groups.collectMany { it ->
            def gv = it.tokenize(':')
            resolveGroupDependencies(gv[0], gv[1])
        })

        dependencies.each { dependency ->
            println "Resolving managed dependencies and parents for ${dependency}"
            processDependency(dependency)
        }

        println "Successfully added ${addedDependencies.size()} dependencies to POM"
        dumpPom(outputDirectory)
    }

    // resolve every artifact for the group and version specified
    def resolveGroupDependencies(group, version) {

        def groupUrl = "https://repo1.maven.org/maven2/${group.replace('.', '/')}/"
        def groupHtml = groupUrl.toURL().text

        def matcher = groupHtml =~ /<a href="(?<artifact>.+)\/" title="\1\/">\1\/<\/a>/
        def allGroupArtifacts = matcher.findAll().collect { it[1] }

        def groupArtifactsAtVersion = allGroupArtifacts.findAll { artifact ->
            def containsVersion = (groupUrl + artifact + "/").toURL().text.contains(/<a href="${version}\/"/)
            if (!containsVersion) {
                println "Could not find version [${version}] for [${group}:${artifact}]"
            }
            containsVersion
        }

        groupArtifactsAtVersion.collect { artifact ->
            def dep = new Dependency(group: group, artifact: artifact, version: version)
            println "Found group dependency [${dep}]"
            dep
        }
    }

    // add a dependency to the POM
    def addDependency(dependency, isPomType) {
        if (addedDependencies.add(dependency) && !DEPENDENCY_BLACKLIST.any { it(dependency) }) {
            println dependency
            def dep = outputPom.dependencies.first().appendNode('dependency')
            dep.appendNode('groupId', dependency.group)
            dep.appendNode('artifactId', dependency.artifact)
            dep.appendNode('version', dependency.version)
            if (isPomType) {
                dep.appendNode('type', 'pom')
            }
        }
    }

    // get pom from cache, if it's not present download and cache it
    def getPom(dependency) {
        def pom = pomCache[dependency]
        if (!pom) {
            pom = new XmlParser().parseText(downloadPom(dependency))
            pomCache[dependency] = pom
        }
        pom
    }

    // download a pom
    def downloadPom(dependency) {
        def pomUrl = repos.findResult {
            def url = "${it.url}${dependency.group.replace('.', '/')}/${dependency.artifact}/${dependency.version}/${dependency.artifact}-${dependency.version}.pom"
            println "Looking for POM for dependency [${dependency}] at URL ${url}"
            try {
                url.toURL().text
            } catch (ignored) {
                println "Couldn't find POM for dependency [${dependency}] at URL ${url}"
                null
            }
        }
        if (pomUrl) {
            println "Found POM for dependency [${dependency}]"
        } else {
            println "Couldn't find POM for dependency [${dependency}] in declared repos"
        }
        pomUrl
    }

    // dump POM to file
    def dumpPom(outputDirectory) {
        (outputDirectory as File).mkdirs()
        def pom = "${outputDirectory}/${outputName}.pom.xml"
        (pom as File).text = XmlUtil.serialize(outputPom)
        pom
    }

    // process a dependency - process, recurse through all parent POMs, and then all managed dependencies, then add dependency to the POM
    def processDependency(dependency) {
        if (!IGNORABLE_ARTIFACTS.contains(dependency.artifact) && !IGNORABLE_GROUPS.contains(dependency.group)) {
            println "Resolving parents and managed dependencies for ${dependency}"

            def pom = getPom(dependency)
            def projectVersion = dependency.version
            def projectGroup = dependency.group

            pom.repositories.repository.each {
                if (addedRepos.add(it.url.text())) {
                    outputPom.repositories.first().append(it)
                }
            }

            def props = pom.properties.isEmpty() ? [:] : pom.properties.first().children().collectEntries { [it.name().getLocalPart(), it.text()] }

            if (!pom.parent.isEmpty()) {
                // resolve parent managed deps and inherit parent properties
                def parent = new Dependency(group: pom.parent.groupId.text(),
                        artifact: pom.parent.artifactId.text(),
                        version: pom.parent.version.text())

                def parentProps = processDependency(parent)

                props = parentProps + props
            }

            pom.dependencyManagement.dependencies.dependency.each {
                processedManagedDependencyNode(it, props, projectVersion, projectGroup)
            }

            addDependency(dependency, !pom.packaging.isEmpty() && pom.packaging.text() == 'pom')

            props
        } else {
            [:]
        }
    }


    // process a managed dependency XML node - if it's got a scope of import, we need to import managed dependencies from it
    def processedManagedDependencyNode(dependencyNode, props, projectVersion, projectGroup) {

        def dependency = new Dependency(group: dependencyNode.groupId.text(),
                artifact: dependencyNode.artifactId.text(),
                version: resolveTextValue(dependencyNode.version.text(), props, projectVersion, projectGroup))

        if (!IGNORABLE_ARTIFACTS.contains(dependency.artifact) && !IGNORABLE_GROUPS.contains(dependency.group)) {


            // resolve the managed deps of any imported BOMs
            if (!dependencyNode.scope.isEmpty() && dependencyNode.scope.text() == 'import') {
                copyDependencyNode(dependencyNode.clone(),
                        dependency,
                        true)

                processDependency(dependency)
            } else {
                copyDependencyNode(dependencyNode.clone(),
                        dependency,
                        false)
            }
        }
    }

    // copy a dependency XML node to the output pom if it isn't there already, and fix the version
    def copyDependencyNode(node, dependency, removeScope) {
        if (addedDependencies.add(dependency)) {
            node.version.first().setValue(dependency.version)
            if (removeScope) {
                node.remove(node.scope.first())
            }
            if (dependency.group == '${project.groupId}') {
                println "Found dodgy dependency $dependency"
                KNOWN_DODGY_DEPENDENCIES.each { artifactPrefix, actualGroup ->
                    if (dependency.artifact.startsWith(artifactPrefix)) {
                        replaceGroup(node, actualGroup)
                    }
                }
            }
            outputPom.dependencies.first().append(node)
        }
    }

    def replaceGroup(node, newGroup) {
        node.remove(node.groupId.first())
        node.appendNode('groupId', newGroup)
    }

    // resolve actual value
    def resolveTextValue(text, props, projectVersion, projectGroup) {
        def matcher = text =~ /^\$\{(?<textValue>.+)\}$/
        if (matcher.matches()) {
            def textValue = matcher.group('textValue')
            if (textValue == 'project.version') {
                projectVersion
            } else if (textValue == 'project.groupId') {
                projectGroup
            } else {
                resolveTextValue(props[textValue], props, projectVersion, projectGroup)
            }
        } else {
            text
        }
    }
}


def cli = new CliBuilder(usage: 'buildPom.groovy [options]')

def allRepoIds = PomBuilder.allRepos.collect { it.id }

cli.with {
    a longOpt: 'artifacts', args: Option.UNLIMITED_VALUES, argName: 'artifacts', valueSeparator: ',', "A comma separated list of GAV parameters for artifacts in the form group:artifact:version. At least one of this or groups must be specified."
    g longOpt: 'groups', args: Option.UNLIMITED_VALUES, argName: 'groups', valueSeparator: ',', "A comma separated list of groups and respective versions in the form group:version. At least one of this or artifacts must be specified."
    o longOpt: 'outputName', args: 1, argName: 'output name', required: true, "name of the POM file, and tar to create"
    k longOpt: 'keepTempFiles', argName: 'keep temporary files', required: false, "keep POM file and unarchived dependencies"
    e longOpt: 'enabled-repos', args: Option.UNLIMITED_VALUES, valueSeparator: ',', "A comma separated list of repo IDs for repos to use. Available repos ${allRepoIds}. Not compatible with disabled-repos"
    d longOpt: 'disabled-repos', args: Option.UNLIMITED_VALUES, valueSeparator: ',', "A comma separated list of repo IDs for repos to disable. Available repos ${allRepoIds}. Not compatible with enabled-repos"
}

def options = cli.parse(args)
if (!options) {
    System.exit(1)
}
if (!options.gs && !options.as) {
    println 'error: at least one of groups or artifacts options must be provided'
    cli.usage()
    System.exit(1)
}
if (options.es && options.ds) {
    println 'error: enabled-repos and disabled-repos are not mutually compatible, use one or the other or neither'
    cli.usage()
    System.exit(1)
}

def artifacts = options.as ? options.as as Set : [] as Set
def groups = options.gs ? options.gs as Set : [] as Set
def enabledRepoIds = options.es ? options.es as Set : [] as Set
def disabledRepoIds = options.ds ? options.ds as Set : [] as Set
def outputName = options.o
def deleteTempFiles = !options.k


def invalidRepoIds = (enabledRepoIds ?: disabledRepoIds) - allRepoIds
if (invalidRepoIds) {
    println "error: the following repo IDs are invalid $invalidRepoIds"
    cli.usage()
    System.exit(1)
}

def outputDirectory = "downloads/${outputName}"
def builder = new PomBuilder(outputName, enabledRepoIds ?: (allRepoIds - disabledRepoIds))
def pomPath = builder.generatePom(artifacts, groups, outputDirectory)


def executeProcess(def procToExecute, def dir = null) {
    def proc = dir ?
            procToExecute.execute(System.getenv()
                    .collect {
                        "${it.key}=$it.value"
                    },
                    dir as File
            ) : procToExecute.execute()
    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()
    if (proc.exitValue()) {
        println "Failed to execute process ${procToExecute}"
    }
    !proc.exitValue()
}


// run mvn package on generated POM, and then tar it up
if (executeProcess(['mvn.cmd', '-B', 'package', '-f', (pomPath as File).getName()], outputDirectory) &&
        executeProcess(['tar', '-czvf', "${outputName}.tar.gz", '--exclude=**/maven-metadata-local.xml', outputName], outputDirectory)) {
    println "Successfully built dependency tarball ${outputName}.tar.gz"
    if (deleteTempFiles) {
        ("$outputDirectory/$outputName" as File).deleteDir()
        (pomPath as File).delete()
    }
} else {
    println """
Failed to generate dependency tarball. Fix errors in $pomPath and then run
cd $outputDirectory &&
mvn -B package -f ${(pomPath as File).getName()} &&
tar -czvf ${outputName}.tar.gz --exclude=**/maven-metadata-local.xml $outputName &&
rm -rf $outputName ${(pomPath as File).getName()}
"""
}

