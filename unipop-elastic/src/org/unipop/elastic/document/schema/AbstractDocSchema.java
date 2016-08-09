package org.unipop.elastic.document.schema;

import io.searchbox.action.BulkableAction;
import io.searchbox.core.Delete;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.shaded.jackson.databind.JsonNode;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.unipop.elastic.common.ElasticClient;
import org.unipop.elastic.common.FilterHelper;
import org.unipop.elastic.document.Document;
import org.unipop.elastic.document.DocumentSchema;
import org.unipop.query.predicates.PredicateQuery;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchQuery;
import org.unipop.schema.element.AbstractElementSchema;
import org.unipop.structure.UniGraph;

import java.io.IOException;
import java.util.*;

public abstract class AbstractDocSchema<E extends Element> extends AbstractElementSchema<E> implements DocumentSchema<E> {
    protected final ElasticClient client;
    protected String type;
    protected String index;
    protected ObjectMapper mapper = new ObjectMapper();

    public AbstractDocSchema(JSONObject configuration, ElasticClient client, UniGraph graph) throws JSONException {
        super(configuration, graph);
        this.client = client;
        this.index = json.optString("index", null);
        this.type = json.optString("type", null);

        client.validateIndex(index);
    }

    @Override
    public String getIndex() {
        return index;
    }

    @Override
    public Search getSearch(SearchQuery<E> query) {
        PredicatesHolder predicatesHolder = this.toPredicates(query.getPredicates());
        if (predicatesHolder.getClause().equals(PredicatesHolder.Clause.Abort)) return null;
        QueryBuilder queryBuilder = createQueryBuilder(predicatesHolder);
        return createSearch(query, queryBuilder);
    }

    protected QueryBuilder createQueryBuilder(PredicatesHolder predicatesHolder) {
        if (predicatesHolder.isAborted()) return null;
        return FilterHelper.createFilterBuilder(predicatesHolder);
    }

    protected Search createSearch(SearchQuery<E> query, QueryBuilder queryBuilder) {
        if(queryBuilder == null) return null;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder)
                .size(query.getLimit() == -1 ? 10000 : query.getLimit());

        if(query.getPropertyKeys() == null) searchSourceBuilder.fetchSource(true);
        else {
            Set<String> fields = toFields(query.getPropertyKeys());
            if(fields.size() == 0) searchSourceBuilder.fetchSource(false);
            else searchSourceBuilder.fetchSource(fields.toArray(new String[fields.size()]), null);
        }

        Search.Builder builder = new Search.Builder(searchSourceBuilder.toString().replace("\n", ""))
                .addIndex(index);
        return builder.build();
    }

    @Override
    public List<E> parseResults(String result, PredicateQuery query) {
        List<E> results = new ArrayList<>();
        try {
            JsonNode hits = mapper.readTree(result).get("hits").get("hits");
            for (JsonNode hit : hits) {
                Map<String, Object> source = hit.has("_source") ? mapper.readValue(hit.get("_source").toString(), Map.class) : new HashMap<>();
                Document document = new Document(hit.get("_index").asText(), hit.get("_type").asText(), hit.get("_id").asText(), source);
                Collection<E> elements = fromDocument(document);
                if(elements != null) {
                    elements.forEach(element -> {
                        if(element != null && query.getPredicates().test(element))
                            results.add(element);
                    });
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    @Override
    public BulkableAction<DocumentResult> addElement(E element, boolean create) {
        Document document = toDocument(element);
        if (document == null) return null;
        Index.Builder builder = new Index.Builder(document.getFields())
                .index(document.getIndex())
                .type(document.getType())
                .id(document.getId());
        if(create) builder.setParameter("op_type", "create");
        return builder.build();
    }

    @Override
    public Delete.Builder delete(E element) {
        Document document = toDocument(element);
        if (document == null) return null;
        return new Delete.Builder(document.getId()).index(document.getIndex()).type(document.getType());
    }

    public Document toDocument(E element) {
        Map<String, Object> fields = this.toFields(element);
        if(fields == null) return null;
        String type = ObjectUtils.firstNonNull(fields.remove("_type"), this.type).toString();
        if (!checkType(type)) return null;
        String id = ObjectUtils.firstNonNull(fields.remove("_id"), fields.remove("id"), element.id()).toString();
        return new Document(index, type, id, fields);
    }

    protected Collection<E> fromDocument(Document document){
        if(!checkType(document.getType())) return null;
        if(!checkIndex(document.getIndex())) return null;
        Map<String, Object> fields = document.getFields();
        fields.put("_id", document.getId());
        fields.put("_type", document.getType());
        return this.fromFields(fields);
    }

    protected boolean checkIndex(String index) {
        if (this.index.contains("*"))
            return index.matches(this.index.replace("*", ".*"));
        return this.index.equals(index);
    }

    protected boolean checkType(String type) {
        return type != null && (this.type == null || this.type.equals(type));
    }

}
