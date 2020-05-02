/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import liquibase.command.CommandResult;

public class CompileXMLCommandTest {

    private CompileXMLCommand command;

    @Before
    public void setUp() {
        command = new CompileXMLCommand();
        command.setOut("target/liquibase-xml");
    }

    @Test
    public void includeAll() throws Exception {
        command.setSrc("net/example/liquibase/groovy/changelog.groovy");
        command.setSingleFile(true);
        assertResult(command.execute());
    }

    @Test
    public void nestedInclude() throws Exception {
        command.setSrc("net/example/liquibase/groovy2/changelog.groovy");
        command.setSingleFile(false);
        assertResult(command.execute());
    }

    private void assertResult(CommandResult result) {
        assertThat("result", result, is(notNullValue()));
        assertThat("result.succeeded", result.succeeded, is(true));
        assertThat("result.message", result.message, is("Compiled successfully"));
        // TODO: Assert content matches against pre-compiled result.
    }

    @After
    public void manualInfo() {
        System.out.println("Check out the result in "
                + Paths.get(command.getOut()).resolve(command.getSrc()).getParent());
    }

}
