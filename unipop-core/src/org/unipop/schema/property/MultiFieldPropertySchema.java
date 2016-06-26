package org.unipop.schema.property;

import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.unipop.query.predicates.PredicatesHolder;

import java.util.*;


public class MultiFieldPropertySchema implements PropertySchema {
    private final String key;
    private final List<String> fields;
    private String delimiter;

    public MultiFieldPropertySchema(String key, List<String> fields, String delimiter, boolean nullable) {
        this.key = key;
        this.fields = fields;
        this.delimiter = delimiter;
    }

    @Override
    public Map<String, Object> toProperties(Map<String, Object> source) {
        String finalValue = null;
        for(String field : fields){
            Object value = source.get(field);
            if(value == null) return null;
            finalValue = finalValue == null ? value.toString() : finalValue + delimiter + value.toString();
        }
        return Collections.singletonMap(key, finalValue);
    }

    @Override
    public Set<String> getFields() {
        return new HashSet<>(this.fields);
    }

    @Override
    public Set<String> getProperties() {
        return Collections.singleton(this.key);
    }

    @Override
    public Map<String, Object> toFields(Map<String, Object> properties) {
        return Collections.emptyMap();
//        Object value = properties.get(this.key);
//        if(value == null) return null;
//        Map<String, Object> result = new HashMap<>(fields.size());
//        String[] values = value.toString().split(delimiter);
//        //TODO: what if values.length != fields.length ??? o_O
//        for(int i = 0; i < fields.size(); i++) {
//            result.put(fields.get(i), values[i]);
//        }
//        return result;
    }

    @Override
    public PredicatesHolder toPredicates(HasContainer has) {
        return null;
    }
}
