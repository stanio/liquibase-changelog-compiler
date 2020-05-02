/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext.test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import liquibase.serializer.AbstractLiquibaseSerializable;
import liquibase.serializer.LiquibaseSerializable;

public class SampleSerializable extends AbstractLiquibaseSerializable {

    private static Set<String> serializableFields = Collections
            .unmodifiableSet(new LinkedHashSet<>(Arrays
                    .asList("namedString", "nestedString", "directString",
                            "namedCollection", "nestedCollection", "directCollection",
                            "namedMap", "nestedMap", "directMap",
                            "namedArray", "nestedArray", "directArray",
                            "namedSerializable", "nestedSerializable", "directSerializable")));

    private String namedString;
    private String nestedString;
    private String directString;

    private Collection<?> namedCollection;
    private Collection<?> nestedCollection;
    private Collection<?> directCollection;

    private Map<String, Object> namedMap;
    private Map<String, Object> nestedMap;
    private Map<String, Object> directMap;

    private Object[] namedArray;
    private Object[] nestedArray;
    private Object[] directArray;

    private LiquibaseSerializable namedSerializable;
    private LiquibaseSerializable nestedSerializable;
    private LiquibaseSerializable directSerializable;

    private String objectNamespace;
    private String objectName;

    public SampleSerializable(String namespace, String name) {
        this.objectNamespace = namespace;
        this.objectName = name;
    }

    public SampleSerializable() {
        this(STANDARD_CHANGELOG_NAMESPACE, "sample");
    }

    public static SampleSerializable newSample() {
        SampleSerializable sample = new SampleSerializable();
        sample.namedString = string("named");
        sample.nestedString = string("nested");
        sample.directString = string("direct");
        sample.namedCollection = list("named");
        sample.nestedCollection = list("nested");
        sample.directCollection = list("direct");
        sample.namedArray = array("named");
        sample.nestedArray = array("nested");
        sample.directArray = array("direct");
        sample.namedMap = map("named");
        sample.nestedMap = map("nested");
        sample.directMap = map("direct");
        sample.namedSerializable = new SimpleSerializable("namedObject");
        sample.nestedSerializable = new SimpleSerializable("nestedObject");
        sample.directSerializable = new SimpleSerializable("directObject");
        return sample;
    }

    public static String string(String prefix) {
        return prefix + "StringValue";
    }
    
    public static List<String> list(String prefix) {
        String[] values = { "Item1", "Item2", "Item3" };
        for (int i = 0; i < values.length; i++) {
            values[i] = prefix + values[i];
        }
        return Arrays.asList(values);
    }

    public static Object[] array(String prefix) {
        Object[] values = { "Element1", "Element2", "Element3" };
        for (int i = 0; i < values.length; i++) {
            values[i] = prefix + values[i];
        }
        return values;
    }

    public static Map<String, Object> map(String prefix) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("foo", prefix + 123);
        map.put("bar", prefix + "Nee");
        map.put("baz", prefix + "Daa");
        return map;
    }

    @Override
    public String getSerializedObjectName() {
        return objectName;
    }

    @Override
    public String getSerializedObjectNamespace() {
        return objectNamespace;
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
        switch (field) {
        case "namedString":
        case "namedCollection":
        case "namedMap":
        case "namedArray":
        case "namedSerializable":
            return SerializationType.NAMED_FIELD;
        case "nestedString":
        case "nestedCollection":
        case "nestedMap":
        case "nestedArray":
        case "nestedSerializable":
            return SerializationType.NESTED_OBJECT;
        case "directString":
        case "directCollection":
        case "directMap":
        case "directArray":
        case "directSerializable":
            return SerializationType.DIRECT_VALUE;
        default:
            return SerializationType.NAMED_FIELD;
        }
    }

    public String getNamedString() {
        return namedString;
    }

    public void setNamedString(String namedString) {
        this.namedString = namedString;
    }

    public String getNestedString() {
        return nestedString;
    }

    public void setNestedString(String nestedString) {
        this.nestedString = nestedString;
    }

    public String getDirectString() {
        return directString;
    }

    public void setDirectString(String directString) {
        this.directString = directString;
    }

    public Collection<?> getNamedCollection() {
        return namedCollection;
    }

    public void setNamedCollection(Collection<?> namedCollection) {
        this.namedCollection = namedCollection;
    }

    public Collection<?> getNestedCollection() {
        return nestedCollection;
    }

    public void setNestedCollection(Collection<?> nestedCollection) {
        this.nestedCollection = nestedCollection;
    }

    public Collection<?> getDirectCollection() {
        return directCollection;
    }

    public void setDirectCollection(Collection<?> directCollection) {
        this.directCollection = directCollection;
    }

    public Map<String, Object> getNamedMap() {
        return namedMap;
    }

    public void setNamedMap(Map<String, Object> namedMap) {
        this.namedMap = namedMap;
    }

    public Map<String, Object> getNestedMap() {
        return nestedMap;
    }

    public void setNestedMap(Map<String, Object> nestedMap) {
        this.nestedMap = nestedMap;
    }

    public Map<String, Object> getDirectMap() {
        return directMap;
    }

    public void setDirectMap(Map<String, Object> directMap) {
        this.directMap = directMap;
    }

    public Object[] getNamedArray() {
        return namedArray;
    }

    public void setNamedArray(Object[] namedArray) {
        this.namedArray = namedArray;
    }

    public Object[] getNestedArray() {
        return nestedArray;
    }

    public void setNestedArray(Object[] nestedArray) {
        this.nestedArray = nestedArray;
    }

    public Object[] getDirectArray() {
        return directArray;
    }

    public void setDirectArray(Object[] directArray) {
        this.directArray = directArray;
    }

    public LiquibaseSerializable getNamedSerializable() {
        return namedSerializable;
    }

    public void setNamedSerializable(LiquibaseSerializable namedSerializable) {
        this.namedSerializable = namedSerializable;
    }

    public LiquibaseSerializable getNestedSerializable() {
        return nestedSerializable;
    }

    public void setNestedSerializable(LiquibaseSerializable nestedSerializable) {
        this.nestedSerializable = nestedSerializable;
    }

    public LiquibaseSerializable getDirectSerializable() {
        return directSerializable;
    }

    public void setDirectSerializable(LiquibaseSerializable directSerializable) {
        this.directSerializable = directSerializable;
    }

}
