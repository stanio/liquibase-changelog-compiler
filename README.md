# Liquibase Compile Command

[CORE-3549][]: Implement "compileChangeLogXml" command

## Try it out

Have a look at the source change logs in:

    src/test/liquibase-groovy/
        net/example/liquibase/groovy/changelog.groovy
        net/example/liquibase/groovy2/changelog.groovy

Run a test:

    mvn test

and have a look at the result XML change logs:

    target/liquibase-xml/
        net/example/liquibase/groovy/changelog.xml
        net/example/liquibase/groovy2/changelog.xml

Now apply the Groovy change log:

    mvn liquibase:update -Dliquibase.changeLogFile=net/example/liquibase/groovy/changelog.groovy
    
You can observe the resulting schema in `target/TestDB` Derby database using
an SQL client of choice (f.e. [SquirrelSQL][]).  Now apply the converted/compiled
XML change log:

    mvn liquibase:update -Dliquibase.changeLogFile=net/example/liquibase/groovy/changelog.xml -Dliquibase.changeLogDirectory=target/liquibase-xml

It should complete successfully and no additional change sets should be applied.

You could clean (drop the database) and repeat with the other variant of the change log:

    mvn clean test liquibase:update -Dliquibase.changeLogFile=net/example/liquibase/groovy2/changelog.groovy
    
    mvn liquibase:update -Dliquibase.changeLogFile=net/example/liquibase/groovy2/changelog.xml -Dliquibase.changeLogDirectory=target/liquibase-xml

[CORE-3549]: https://liquibase.jira.com/browse/CORE-3549
[SquirrelSQL]: http://www.squirrelsql.org/
