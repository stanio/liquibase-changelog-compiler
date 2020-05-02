/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */
package net.example.liquibase.serializer.ext;

import java.util.LinkedHashSet;
import java.util.Set;

import liquibase.LabelExpression;
import liquibase.changelog.ChangeLogInclude;

public class EnhancedChangeLogInclude extends ChangeLogInclude {

    private LabelExpression labels;

    @Override
    public Set<String> getSerializableFields() {
        Set<String> complete = new LinkedHashSet<>(super.getSerializableFields());
        complete.add("labels");
        return complete;
    }

    public LabelExpression getLabels() {
        return labels;
    }

    public void setLabels(LabelExpression labels) {
        this.labels = labels;
    }

}
