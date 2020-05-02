/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.command.ext;

import static net.example.liquibase.command.ext.EnhancedXMLChangeLogSerializer.xmlExt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import liquibase.ContextExpression;
import liquibase.Labels;
import liquibase.changelog.ChangeLogChild;
import liquibase.changelog.ChangeLogInclude;
import liquibase.changelog.ChangeLogParameters.ChangeLogParameter;
import liquibase.changelog.ChangeLogProperty;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.logging.LogService;
import liquibase.precondition.Precondition;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.precondition.core.PreconditionContainer.ErrorOption;
import liquibase.precondition.core.PreconditionContainer.FailOption;
import liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.serializer.LiquibaseSerializable.SerializationType;
import net.example.liquibase.command.ext.ChangeLogAttributesFilter.ChangeSetKey;

class ChangeLogContent {

    private List<DatabaseChangeLog> dbChangeLogs = new ArrayList<>();
    private Map<DatabaseChangeLog, List<ChangeLogChild>> contentMap = new IdentityHashMap<>();
    private Map<ChangeSetKey, String> logicalFileMap = new HashMap<>();
    private boolean singleFile;

    ChangeLogContent(DatabaseChangeLog changeLog, boolean singleFile) {
        this.singleFile = singleFile;
        
        // https://www.liquibase.org/documentation/preconditions.html
        // Preconditions at the changelog level apply to all changeSets, not
        // just those listed in the current changelog or its child changelogs.
        PreconditionContainer preconditions = changeLog.getPreconditions();
        if (preconditionsPresent(preconditions)) {
            addContent(changeLog).add(preconditions);
        }
        init(changeLog);
    }

    private void init(DatabaseChangeLog changeLog) {
        List<ChangeLogChild> content = addContent(changeLog);
        String basePath = changeLog.getLogicalFilePath();
        for (ChangeSet changeSet : changeLog.getChangeSets()) {
            DatabaseChangeLog nextLog = changeSet.getChangeLog();
            if (nextLog == changeLog || singleFile) {
                content.add(changeSet);
                mapChange(basePath, changeSet);
            } else if (!contentMap.containsKey(nextLog)) {
                ChangeLogInclude include = new ChangeLogInclude();
                include.setFile(urlPath(xmlExt(nextLog.getPhysicalFilePath())));
                ContextExpression includeContexts = nextLog.getIncludeContexts();
                if (includeContexts != null && !includeContexts.isEmpty()) {
                    include.setContext(includeContexts);
                }
                content.add(include);
                init(nextLog);
            }
        }
    }

    private void mapChange(String basePath, ChangeSet changeSet) {
        if (basePath.equals(changeSet.getFilePath())) {
            return;
        }
        if (logicalFileMap.put(ChangeSetKey.get(changeSet),
                               changeSet.getFilePath()) != null) {
            LogService.getLog(EnhancedXMLChangeLogSerializer.class)
                    .warning("Duplicate change detected: " + ChangeSetKey.get(changeSet));
        }
    }

    public List<DatabaseChangeLog> getChangeLogs() {
        return Collections.unmodifiableList(dbChangeLogs);
    }

    public List<ChangeLogChild> getContent(DatabaseChangeLog changeLog) {
        Objects.requireNonNull(changeLog, "changeLog must not be null");

        List<ChangeLogChild> content = contentMap.get(changeLog);
        if (content == null) {
            throw new NoSuchElementException();
        }
        return Collections.unmodifiableList(content);
    }

    public Map<ChangeSetKey, String> getLogicalFileMap() {
        return Collections.unmodifiableMap(logicalFileMap);
    }

    private List<ChangeLogChild> addContent(DatabaseChangeLog changeLog) {
        Objects.requireNonNull(changeLog, "changeLog must not be null");

        List<ChangeLogChild> content = contentMap.get(changeLog);
        if (content == null) {
            content = new ArrayList<>();
            addDeclaredProperties(changeLog, content, singleFile);
            contentMap.put(changeLog, content);
            dbChangeLogs.add(changeLog);
        }
        return content;
    }

    private static Map<String, Object> defaultPreconditionAttributes;
    static {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("onError", ErrorOption.HALT);
        attributes.put("onFail", FailOption.HALT);
        attributes.put("onSqlOutput", OnSqlOutputOption.IGNORE);
        defaultPreconditionAttributes = attributes;
    }

    private static boolean preconditionsPresent(PreconditionContainer preconditions) {
        if (preconditions == null) {
            return false;
        }
        Map<String, Object> attributes = getSerializableAttributes(preconditions);
        attributes.entrySet().removeAll(defaultPreconditionAttributes.entrySet());
        if (!attributes.isEmpty()) {
            return true;
        }

        for (Precondition nested : preconditions.getNestedPreconditions()) {
            if (nested instanceof PreconditionContainer) {
                if (preconditionsPresent((PreconditionContainer) nested)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> getSerializableAttributes(LiquibaseSerializable serializable) {
        Map<String, Object> attributes = new HashMap<>();
        for (String name : serializable.getSerializableFields()) {
            SerializationType type = serializable.getSerializableFieldType(name);
            if (type == SerializationType.NAMED_FIELD) {
                Object value = serializable.getSerializableFieldValue(name);
                if (value == null || value instanceof Collection) {
                    continue;
                }
                attributes.put(name, value);
            }
        }
        return attributes;
    }

    /*
     * https://www.liquibase.org/documentation/changelog_parameters.html
     */
    private static void addDeclaredProperties(DatabaseChangeLog changeLog,
                                              List<ChangeLogChild> content,
                                              boolean allChangeLogs) {
        for (ChangeLogParameter param : changeLog.getChangeLogParameters().getChangeLogParameters()) {
            if (param.getChangeLog() == null) {
                continue;
            }
            if (allChangeLogs || param.getChangeLog() == changeLog) {
                content.add(initProperty(param));
            }
        }
    }

    private static ChangeLogProperty initProperty(ChangeLogParameter param) {
        ChangeLogProperty prop = new ChangeLogProperty();
        prop.setName(param.getKey());
        prop.setValue(param.getValue().toString());
        //prop.setFile(null);
        prop.setDbms(stringValueOf(param.getValidDatabases()));
        prop.setLabels(stringValueOf(param.getLabels()));
        prop.setContext(stringValueOf(param.getValidContexts()));
        if (!param.isGlobal()) {
            prop.setGlobal(false);
        }
        return prop;
    }

    private static String stringValueOf(Iterable<String> values) {
        return (values == null) ? null : String.join(",", values);
    }

    private static String stringValueOf(ContextExpression contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        return contexts.toString();
    }

    private static String stringValueOf(Labels labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        return labels.toString();
    }

    private static String urlPath(String path) {
        return path.replace('\\', '/');
    }

}
