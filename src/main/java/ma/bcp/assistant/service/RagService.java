package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

@ApplicationScoped
public class RagService {

    private static final Logger log = Logger.getLogger(RagService.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String COLLECTION_ID = "8261c270-6b39-4d68-9250-7509a6fd3c52";;
    private static final int TOP_K = 3;

    @ConfigProperty(name = "ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaUrl;

    @ConfigProperty(name = "chroma.base-url", defaultValue = "http://localhost:8100")
    String chromaUrl;

    public String getContext(String query) {
        try {
            double[] embedding = getEmbedding(query);
            if (embedding == null || embedding.length == 0) return "";
            log.info("[RAG] Embedding : " + embedding.length + " dims");
            return queryChroma(embedding);
        } catch (Exception e) {
            log.warning("[RAG] Erreur : " + e.getMessage());
            return "";
        }
    }

    private double[] getEmbedding(String query) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of(
            "model", "nomic-embed-text", "prompt", query
        ));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/embeddings"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> res = newClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new RuntimeException("Ollama: " + res.statusCode());
        var node = mapper.readTree(res.body()).get("embedding");
        if (node == null) throw new RuntimeException("Embedding null");
        double[] emb = new double[node.size()];
        for (int i = 0; i < node.size(); i++) emb[i] = node.get(i).asDouble();
        return emb;
    }

    private String queryChroma(double[] embedding) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode queryEmbeddings = root.putArray("query_embeddings");
        ArrayNode currentEmbedding = queryEmbeddings.addArray();
        for (double v : embedding) {
            currentEmbedding.add(v);
        }
        root.put("n_results", TOP_K);
        ArrayNode include = root.putArray("include");
        include.add("documents").add("distances");

        String body = mapper.writeValueAsString(root);
        
        int logLen = Math.min(body.length(), 100);
        log.info("[RAG] Sending to Chroma: " + body.substring(0, logLen) + "...");

        String url = chromaUrl + "/api/v2/tenants/default_tenant/databases/default_database/collections/" + COLLECTION_ID + "/query";
        
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json") 
            .header("Accept", "application/json")       
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> res = newClient().send(req, HttpResponse.BodyHandlers.ofString());
        
        if (res.statusCode() != 200) {
            throw new RuntimeException("ChromaDB Error " + res.statusCode() + ": " + res.body());
        }
        
        return parseChromaResponse(res.body());    
    }

    private String parseChromaResponse(String responseBody) throws Exception {
        var json = mapper.readTree(responseBody);
        var documents = json.get("documents");
        if (documents == null || documents.isEmpty()) return "";
        var docsArray = documents.get(0);
        if (docsArray == null || docsArray.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docsArray.size(); i++) {
            String doc = docsArray.get(i).asText();
            if (doc.isBlank()) continue;
            if (sb.length() > 0) sb.append("\n\n---\n\n");
            sb.append(doc);
        }
        log.info("[RAG] Contexte : " + sb.length() + " chars");
        return sb.toString();
    }

    // LE CORRECTIF EST ICI : .version(HttpClient.Version.HTTP_1_1)
    private HttpClient newClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }
}
