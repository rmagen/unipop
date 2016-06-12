package org.unipop.common.util;

import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.json.JSONArray;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ConversionUtils {

    public static <T> Stream<T> asStream(Iterator<T> sourceIterator) {
        return asStream(sourceIterator, false);
    }

    public static <T> Stream<T> asStream(Iterator<T> sourceIterator, boolean parallel) {
        Iterable<T> iterable = () -> sourceIterator;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    public static <T> Set<T> toSet(JSONArray jsonArray) {
        HashSet<T> hashSet = new HashSet<>(jsonArray.length());
        for(int i = 0; i < jsonArray.length(); i++)
            hashSet.add((T) jsonArray.get(i));
        return hashSet;
    }

    public static Map<String, Object> asMap(Object[] keyValues){
        Map<String, Object> map = new HashMap<>();
        if (keyValues != null) {
            ElementHelper.legalPropertyKeyValueArray(keyValues);
            for (int i = 0; i < keyValues.length; i = i + 2) {
                String key = keyValues[i].toString();
                Object value = keyValues[i + 1];
                ElementHelper.validateProperty(key,value);
                map.put(key, value);
            }
        }
        return map;
    }
}