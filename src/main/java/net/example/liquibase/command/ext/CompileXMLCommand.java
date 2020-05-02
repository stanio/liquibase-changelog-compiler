/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import java.util.ArrayList;
import java.util.List;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.command.AbstractCommand;
import liquibase.command.CommandResult;
import liquibase.command.CommandValidationErrors;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

/**
 * @see  liquibase.sdk.convert.ConvertCommand
 */
public class CompileXMLCommand extends AbstractCommand<CommandResult> {

    public static final String NAME = "compileXml";

    private String src;
    private String out;
    private String classpath;
    private boolean singleFile;

    /**
     * {@value #NAME}
     */
    @Override
    public String getName() {
        return NAME;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    public boolean isSingleFile() {
        return singleFile;
    }

    public void setSingleFile(boolean singleFile) {
        this.singleFile = singleFile;
    }

    @Override
    protected CommandResult run() throws Exception {
        List<ResourceAccessor> openers = new ArrayList<>();
        openers.add(new FileSystemResourceAccessor());
        openers.add(new ClassLoaderResourceAccessor());
        if (getClasspath() != null) {
            openers.add(new FileSystemResourceAccessor(getClasspath()));
        }
        ResourceAccessor resourceAccessor = new CompositeResourceAccessor(openers);

        ChangeLogParser sourceParser = ChangeLogParserFactory.getInstance().getParser(getSrc(), resourceAccessor);
        DatabaseChangeLog changeLog = sourceParser.parse(getSrc(), new ChangeLogParameters(), resourceAccessor);
        EnhancedXMLChangeLogSerializer enhancedSerializer = new EnhancedXMLChangeLogSerializer();
        enhancedSerializer.serialize(changeLog, getOut(), isSingleFile());

        return new CommandResult("Compiled successfully");
    }

    @Override
    public CommandValidationErrors validate() {
        return new CommandValidationErrors(this);
    }

}
