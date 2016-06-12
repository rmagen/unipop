package org.unipop.elastic;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.searchbox.client.JestClient;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.unipop.common.test.UnipopGraphProvider;
import org.unipop.elastic.common.ElasticClientFactory;
import org.unipop.elastic.common.ElasticHelper;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.Map;

public class ElasticGraphProvider extends UnipopGraphProvider {

    private static String CLUSTER_NAME = "unipop";
    private static String CONFIGURATION = "basic.json";
    private Client client;

    public ElasticGraphProvider() throws Exception{
        //patch for failing IO tests that write to disk
        System.setProperty("build.dir", System.getProperty("user.dir") + "\\build");
        //Delete elasticsearch 'data' directory
        String path = new java.io.File( "." ).getCanonicalPath() + "\\data";
        File file = new File(path);
        FileUtils.deleteQuietly(file);

//        Node node = ElasticClientFactory.createNode(CLUSTER_NAME, false, 9300);

        JsonObject object = new JsonParser()
                .parse(new FileReader(Resources.getResource("configuration/" + CONFIGURATION).getFile()))
                .getAsJsonObject();

        this.client = ElasticClientFactory.createNode(CLUSTER_NAME, false, 9300).client();
    }

    @Override
    public Map<String, Object> getBaseConfiguration(String graphName, Class<?> test, String testMethodName, LoadGraphWith.GraphData loadGraphWith) {
        Map<String, Object> baseConfiguration = super.getBaseConfiguration(graphName, test, testMethodName, loadGraphWith);
        URL url = this.getClass().getResource("/configuration/" + CONFIGURATION);
        baseConfiguration.put("providers", new String[]{url.getFile()});
        return baseConfiguration;
    }

    @Override
    public void clear(Graph g, Configuration configuration) throws Exception {
        String indexName = configuration.getString("graphName");
        if(g != null && indexName != null) ElasticHelper.deleteIndices(client);
        super.clear(g, configuration);
    }


    @Override
    public Object convertId(Object id, Class<? extends Element> c) {
        return id.toString();
    }

    public Client getClient() {
        return client;
    }
}
