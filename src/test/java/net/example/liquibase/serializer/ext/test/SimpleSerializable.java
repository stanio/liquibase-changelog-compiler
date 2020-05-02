/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext.test;

import java.util.Collections;
import java.util.Set;

import liquibase.serializer.AbstractLiquibaseSerializable;

public class SimpleSerializable extends AbstractLiquibaseSerializable {

    private static Set<String> serializableFields = Collections.singleton("value");

    private String value;

    public SimpleSerializable() {
        this(null);
    }

    public SimpleSerializable(String value) {
        this.value = value;
    }

    @Override
    public String getSerializedObjectName() {
        return "simple";
    }

    @Override
    public String getSerializedObjectNamespace() {
        //return "urn:x-test:simple";
        return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
    }

    @Override
    public Set<String> getSerializableFields() {
        return serializableFields;
    }

    @Override
    public String getSerializableFieldNamespace(String field) {
        return getSerializedObjectNamespace();
    }

    @Override
    public SerializationType getSerializableFieldType(String field) {
        return SerializationType.DIRECT_VALUE;
    }

    @Override
    public Object getSerializableFieldValue(String field) {
        return getValue();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
