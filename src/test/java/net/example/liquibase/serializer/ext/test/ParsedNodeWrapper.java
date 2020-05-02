/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import liquibase.parser.core.ParsedNode;
import liquibase.parser.core.ParsedNodeException;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.LiquibaseSerializable;

public class ParsedNodeWrapper implements LiquibaseSerializable {

    private transient ParsedNode parsedNode;

    private transient Map<String, String> fields;

    public ParsedNodeWrapper(ParsedNode parsedNode) {
        this.parsedNode = parsedNode;
    }
    
    private Map<String, String> getFields() {
        if (fields == null) {
            Map<String, String> map = new LinkedHashMap<>();
            for (ParsedNode child : parsedNode.getChildren()) {
                if (map.put(child.getName(), child.getNamespace()) != null) {
                    System.err.println("Duplicate key found: " + child.getName());
                }
            }
            if (parsedNode.getValue() != null) {
                if (map.put("value", parsedNode.getNamespace()) != null) {
                    System.err.println("Duplicate key found: value");
                }
            }
            fields = map;
        }
        return fields;
    }

    @Override
    public String getSerializedObjectName() {
        return parsedNode.getName();
    }

    @Override
    public Set<String> getSerializableFields() {
        return getFields().keySet();
    }

    @Override
    public Object getSerializableFieldValue(String field) {
        if (field.equals("value")) {
            return parsedNode.getValue();
        }
        try {
            return new ParsedNodeWrapper(parsedNode.getChild(fields.get(field), field));
        } catch (ParsedNodeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializationType getSerializableFieldType(String field) {
        if (field.equals("value") && parsedNode.getValue() != null) {
            return SerializationType.DIRECT_VALUE;
        }

        Object value = getSerializableFieldValue(field);
        if (value instanceof ParsedNodeWrapper) {
            return SerializationType.NESTED_OBJECT;
        }
        return SerializationType.NAMED_FIELD;
    }

    @Override
    public String getSerializableFieldNamespace(String field) {
        //return getSerializedObjectNamespace();
        return fields.get(field);
    }

    @Override
    public String getSerializedObjectNamespace() {
        return parsedNode.getNamespace();
    }

    @Override
    public void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
        this.parsedNode = parsedNode;
    }

    @Override
    public ParsedNode serialize() throws ParsedNodeException {
        return parsedNode;
    }

}
