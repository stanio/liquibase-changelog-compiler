/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.gradle

import java.util.Set

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import net.example.liquibase.groovy.CustomXMLChangeLogSerializer

class LiquibaseCompile extends DefaultTask {

    @Input
    String changeLogFile

    @Input
    Set<String> classpath

    @TaskAction
    def compile() {
        def serializer = new CustomXMLChangeLogSerializer()
        serializer.serialize(classpath, changeLogFile,
                outputs.files.singleFile.toString().toString())
    }

}
