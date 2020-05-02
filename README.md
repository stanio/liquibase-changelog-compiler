# Liquibase Changelog Compiler

Sandbox for [CORE-3549][]: Implement "compileChangeLogXml" command

This project tries to implement the following build-time facilities:

-   Convert source changelog to the built-in XML format

    This would allow applying the migrations from a runtime without extra
    dependencies to read other source formats, f.e. Groovy.

    _Note,_ there's a [`ConvertCommand`](https://www.liquibase.org/javadoc/liquibase/sdk/convert/ConvertCommand.html)
    which suffers from the current `XMLChangeLogSerializer` implementation
    deficiencies outlined further below;

-   Output either single file, or reconstruct source file structure

    The single file may be faster the load, but there are some constraints
    which cannot be recreated.

    The supplied [`XMLChangeLogSerializer`](https://www.liquibase.org/javadoc/liquibase/serializer/core/xml/XMLChangeLogSerializer.html)
    can output just to a single file, and further:

    -   Doesn't output `<databaseChangeLog logicalFilePath="...">` or
         `<changeSet logicalFilePath="...">` attributes, if necessary;
    -   Doesn't output `<databaseChangeLog context="...">` or
        `<changeSet context="...">`, if necessary;
    -   Doesn't output changelog [properties](https://www.liquibase.org/documentation/changelog_parameters.html) or
        changelog (vs. changeset) [preconditions](https://www.liquibase.org/documentation/preconditions.html);
    -   Doesn't resolve `<loadData file="...">` and in similar refactorings, if necessary;
    -   Other smaller inconsistencies.

So the main thing this project provides currently is an `EnhancedXMLChangeLogSerializer`
which extends (and re-implements internally) `XMLChangeLogSerializer`.

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
