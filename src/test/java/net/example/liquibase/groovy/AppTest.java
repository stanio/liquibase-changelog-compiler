/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.groovy;

import static org.junit.Assert.*;

import org.junit.Test;

public class AppTest {

    @Test
    public void testAppHasAGreeting() {
        App app = new App();
        assertNotNull("app should have a greeting", app.getGreeting());
    }

}
