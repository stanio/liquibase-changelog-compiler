/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.groovy;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

import org.apache.derby.jdbc.BasicEmbeddedDataSource40;
import org.apache.derby.jdbc.EmbeddedDataSourceInterface;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

public class App {

    private static final String DB_NAME = "SampleDB";

    private EmbeddedDataSourceInterface ds;

    App() {
        ds = new BasicEmbeddedDataSource40();
        ds.setDatabaseName(DB_NAME);
    }

    public String getGreeting() {
        return "Hello people!";
    }

    public void setUp() throws SQLException, LiquibaseException {
        ds.setCreateDatabase("create");
        try (Connection con = ds.getConnection()) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Liquibase liquibase =
                    new Liquibase("net/example/liquibase/groovy/changelog.xml",
                                  new ClassLoaderResourceAccessor(contextClassLoader),
                                  new JdbcConnection(con));
            liquibase.update(new Contexts());
        } finally {
            ds.setCreateDatabase(null);
        }
    }

    public void run() throws SQLException {
        System.out.println(getGreeting());
        try (Connection con = ds.getConnection()) {
            listPeople(con, System.out);
        }
    }

    public void tearDown() throws SQLException {
        ds.setShutdownDatabase("shutdown");
        try (Connection con = ds.getConnection()) {
            System.err.print("Database '" + DB_NAME +"' not shut down?");
        } catch (SQLException e) {
            if ("08006".equals(e.getSQLState())) {
                System.out.println(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private void listPeople(Connection con, PrintStream out) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("SELECT * FROM people")) {
            stmt.setMaxRows(100);

            try (ResultSet rs = stmt.executeQuery()) {
                print(out, rs);
            }
        }
    }

    private static void print(PrintStream out, ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int[] columnSizes = printHeader(out, metaData);
        int rowCount = 0;
        while (rs.next()) {
            rowCount += 1;
            for (int i = 1; i < columnSizes.length; i++) {
                if (i > 1) {
                    out.print(' ');
                }
                out.printf(Locale.ROOT, "%-" + columnSizes[i] + "s", rs.getString(i));
            }
            out.println();
        }
        out.println("\t" + rowCount + " rows");
    }

    private static int[] printHeader(PrintStream out, ResultSetMetaData metaData)
            throws SQLException
    {
        char[] line = new char[100];
        Arrays.fill(line, '-');

        final int columnCount = metaData.getColumnCount();
        int[] columnSizes = new int[columnCount + 1];
        StringBuilder headerLine = new StringBuilder(100);
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                out.print(' ');
                headerLine.append(' ');
            }
            String label = metaData.getColumnLabel(i);
            int width = Math.max(label.length(), metaData.getColumnDisplaySize(i));
            out.printf(Locale.ROOT, "%-" + width + "s", label);
            headerLine.append(line, 0, width);
            columnSizes[i] = width;
        }
        out.println();
        out.println(headerLine);
        return columnSizes;
    }

    public static void main(String[] args) throws Exception {
        App app = new App();
        try {
            app.setUp();
            app.run();
        } finally {
            app.tearDown();
        }
    }

}
