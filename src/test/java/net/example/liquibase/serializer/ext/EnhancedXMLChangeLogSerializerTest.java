/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext;

import static liquibase.serializer.LiquibaseSerializable.GENERIC_CHANGELOG_EXTENSION_NAMESPACE;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import liquibase.serializer.core.xml.XMLChangeLogSerializer;
//import liquibase.serializer.core.xml.XMLChangeLogSerializerFixed;
import net.example.liquibase.serializer.ext.EnhancedXMLChangeLogSerializer;
import net.example.liquibase.serializer.ext.test.SampleSerializable;
import net.example.liquibase.serializer.ext.test.SimpleSerializable;

public class EnhancedXMLChangeLogSerializerTest {

    private static XMLChangeLogSerializer standardSerializer;
    //private static XMLChangeLogSerializerFixed fixedSerializer;
    private static EnhancedXMLChangeLogSerializer enhancedSerializer;

    @BeforeClass
    public static void init() {
        standardSerializer = new XMLChangeLogSerializer();
        //fixedSerializer = new XMLChangeLogSerializerFixed();
        enhancedSerializer = new EnhancedXMLChangeLogSerializer();
    }

    @Test
    public void serializeSingleObject() throws Exception {
        //SampleSerializable object = SampleSerializable.newSample();

        SampleSerializable object = new SampleSerializable();
        SampleSerializable child = new SampleSerializable(GENERIC_CHANGELOG_EXTENSION_NAMESPACE, "npoba");
        //child.setNestedMap(SampleSerializable.map("named"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("foo", Arrays.asList(new SimpleSerializable("cxvxvxvrewr"), new SimpleSerializable("oyuoyupo")));
        values.put("bar", Arrays.asList(new SimpleSerializable("vbvrter retrete"), new SimpleSerializable("bnmburote")));
        //values.put("foo", "tretretre tretret");
        //values.put("bar", "cvxcvxrewrwe ewrewr");
        child.setNestedMap(values);
        object.setDirectSerializable(child);

//        SampleSerializable object = new SampleSerializable();
//        Map<String, Object> values = new LinkedHashMap<>();
//        values.put("foo", new SimpleSerializable("cxvxvxvrewr"));
//        values.put("bar", new SimpleSerializable("vbvrter retrete"));
//        object.setNamedMap(values);

        System.out.println(standardSerializer.serialize(object, true));
        //System.out.println(fixedSerializer.serialize(object, true));
        System.out.println(enhancedSerializer.serialize(object, true));
    }

}
