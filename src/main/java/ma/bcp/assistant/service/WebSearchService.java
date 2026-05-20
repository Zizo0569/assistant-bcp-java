package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSearchService — Recherche DuckDuckGo pour enrichir le contexte BCP
 *
 * Utilisé par OllamaService quand le RAG ChromaDB est insuffisant.
 * Recherche uniquement sur les domaines BCP officiels.
 */
@ApplicationScoped
public class WebSearchService {

    private static final Logger log = Logger.getLogger(WebSearchService.class.getName());

    // Seuil : si le contexte RAG est < MIN_CONTEXT_CHARS, on fait une recherche web
    public static final int MIN_CONTEXT_CHARS = 200;

    // Domaines BCP officiels — pour filtrer les résultats pertinents
    private static final String BCP_SITE_FILTER =
        "site:groupebanquepopulaire.ma OR site:creditduclic.ma OR site:banquepopulaire.ma";

    private final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────
    // Point d'entrée — appelé par OllamaService
    // ─────────────────────────────────────────────────────────
    public String search(String query) {
        try {
            // Construire la requête avec filtre BCP
            String fullQuery = query + " BCP Banque Populaire Maroc " + BCP_SITE_FILTER;
            String encoded   = URLEncoder.encode(fullQuery, StandardCharsets.UTF_8);

            // DuckDuckGo Instant Answer API (gratuit, pas de clé requise)
            String url = "https://api.duckduckgo.com/?q=" + encoded
                       + "&format=json&no_html=1&skip_disambig=1&no_redirect=1";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "BCP-Assistant/1.0")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                String context = parseDuckDuckGoResponse(response.body(), query);
                log.info("[WEB] Contexte web : " + context.length() + " chars");
                return context;
            }

        } catch (Exception e) {
            log.warning("[WEB] Erreur DuckDuckGo : " + e.getMessage());
        }

        // Fallback — recherche HTML simple
        return searchFallback(query);
    }

    // ─────────────────────────────────────────────────────────
    // Parser la réponse DuckDuckGo Instant Answer API
    // ─────────────────────────────────────────────────────────
    private String parseDuckDuckGoResponse(String body, String query) {
        try {
            var json = mapper.readTree(body);
            StringBuilder sb = new StringBuilder();

            // Abstract — résumé principal
            String abstractText = json.path("Abstract").asText("");
            if (!abstractText.isBlank()) {
                sb.append(abstractText).append("\n\n");
            }

            // Answer — réponse directe si disponible
            String answer = json.path("Answer").asText("");
            if (!answer.isBlank()) {
                sb.append("Réponse directe : ").append(answer).append("\n\n");
            }

            // RelatedTopics — sujets connexes
            var topics = json.path("RelatedTopics");
            int count = 0;
            for (var topic : topics) {
                if (count >= 3) break;
                String text = topic.path("Text").asText("");
                if (!text.isBlank() && text.toLowerCase().contains("bcp")
                    || text.toLowerCase().contains("banque")
                    || text.toLowerCase().contains("crédit")
                    || text.toLowerCase().contains("maroc")) {
                    sb.append("• ").append(text).append("\n");
                    count++;
                }
            }

            // Definition
            String definition = json.path("Definition").asText("");
            if (!definition.isBlank()) {
                sb.append("\n").append(definition);
            }

            return sb.toString().trim();

        } catch (Exception e) {
            log.warning("[WEB] Erreur parsing DDG : " + e.getMessage());
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────
    // Fallback — HTML scraping simplifié si API DDG vide
    // ─────────────────────────────────────────────────────────
    private String searchFallback(String query) {
        try {
            // Recherche directe sur le site BCP
            String bcpQuery = URLEncoder.encode(
                query + " banque populaire maroc", StandardCharsets.UTF_8
            );
            String url = "https://html.duckduckgo.com/html/?q=" + bcpQuery;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 BCP-Assistant")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return extractSnippets(response.body());
            }

        } catch (Exception e) {
            log.warning("[WEB] Fallback erreur : " + e.getMessage());
        }
        return "";
    }

    // ─────────────────────────────────────────────────────────
    // Extraire les snippets de résultats HTML DuckDuckGo
    // ─────────────────────────────────────────────────────────
    private String extractSnippets(String html) {
        StringBuilder sb = new StringBuilder();

        // Extraire les snippets de résultats (balises <a class="result__snippet">)
        Pattern pattern = Pattern.compile(
            "class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(html);

        int count = 0;
        while (matcher.find() && count < 3) {
            String snippet = matcher.group(1)
                .replaceAll("<[^>]+>", "")   // supprimer les balises HTML
                .replaceAll("\\s+", " ")      // normaliser les espaces
                .trim();

            // Filtrer pour garder seulement les snippets pertinents BCP
            String lower = snippet.toLowerCase();
            if (!snippet.isBlank() && snippet.length() > 30) {
               sb.append("• ").append(snippet).append("\n");
               count++;
            }
        }

        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────────────────
    // Vérifier si le contexte RAG est suffisant
    // ─────────────────────────────────────────────────────────
    public boolean isContextInsuffisant(String ragContext) {
        return ragContext == null || ragContext.trim().length() < MIN_CONTEXT_CHARS;
    }
}