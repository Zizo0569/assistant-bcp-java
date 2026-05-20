package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

@ApplicationScoped
public class OllamaService {

    private static final Logger log = Logger.getLogger(OllamaService.class.getName());

    @Inject RagService       rag;
    @Inject WebSearchService webSearch;
    @Inject ConversationStore store;

    @ConfigProperty(name = "ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaUrl;

    @ConfigProperty(name = "ollama.model", defaultValue = "qwen2:0.5b")
    String model;

    @ConfigProperty(name = "ollama.timeout", defaultValue = "120")
    int timeoutSeconds;

    private static final String BCP_KNOWLEDGE = """
        === BANQUE POPULAIRE DU MAROC (BCP) � BASE DE CONNAISSANCE ===

        IDENTITE :
        - Nom complet : Banque Centrale Populaire (BCP) / Groupe Banque Populaire
        - R�seau : plus de 1 300 agences au Maroc, pr�sence internationale
        - Service client : 080 200 80 80 (gratuit, 24h/24, 7j/7)
        - Application mobile : BCP Mobile (iOS et Android), Chaabi Net (web)
        - Horaires agences : Lundi-Vendredi 8h30-17h00, Samedi 9h00-13h00

        CREDITS IMMOBILIERS :
        - Taux fixe � partir de 4,5% / an
        - Dur�e : jusqu'� 25 ans
        - Financement jusqu'� 100% du bien pour les MRE
        - Documents requis : CIN, bulletins de salaire (3 derniers), relev�s bancaires (3 mois)

        CREDITS AUTO :
        - Taux � partir de 5% / an, dur�e 1 � 7 ans
        - Financement jusqu'� 80% du prix du v�hicule

        CREDITS CONSOMMATION :
        - Cr�dit personnel : jusqu'� 500 000 DH, dur�e 1 � 7 ans

        COMPTES BANCAIRES :
        - Hissab Bikhir : compte sans frais, id�al jeunes et �tudiants
        - Compte MRE : d�di� aux Marocains R�sidant � l'�tranger
        - Compte �pargne, compte courant classique

        CARTES BANCAIRES :
        - Carte Populaire, Visa Classic/Gold/Platinum, Mastercard
        - Pocket Bank : carte pour les 16-25 ans
        - Carte Pr�pay�e rechargeable

        SERVICES DIGITAUX :
        - BCP Mobile (iOS/Android), Chaabi Net (web)
        - Paiement sans contact HCE, virements, paiement de factures

        CONTACTS : 080 200 80 80 | www.groupebcp.com
        """;

    private static final java.util.Map<String, String> LANGUE_INSTRUCTION = java.util.Map.of(
        "ar", "IMPORTANT: R�ponds UNIQUEMENT en arabe classique (??????? ??????). Registre professionnel bancaire.",
        "da", "IMPORTANT: R�ponds UNIQUEMENT en darija marocain (???????). Sois naturel et accessible.",
        "fr", "IMPORTANT: R�ponds UNIQUEMENT en fran�ais. Registre professionnel et courtois.",
        "en", "IMPORTANT: Respond ONLY in English. Professional and courteous banking tone."
    );

    public String ask(String sessionId, String userMessage, String langue) {
        String langueInstr = LANGUE_INSTRUCTION.getOrDefault(langue, LANGUE_INSTRUCTION.get("fr"));

        // Étape 1 — RAG ChromaDB
        String ragContext = rag.getContext(userMessage);
        log.info("[RAG] " + ragContext.length() + " chars");

        // Étape 2 — DuckDuckGo si RAG insuffisant
        String webContext = "";
        if (webSearch.isContextInsuffisant(ragContext)) {
            log.info("[WEB] RAG insuffisant → DuckDuckGo");
            webContext = webSearch.search(userMessage);
            log.info("[WEB] " + webContext.length() + " chars");
        }

        // Étape 3 — Historique (On récupère les 4 derniers messages)
        java.util.List<ma.bcp.assistant.model.ConversationLog> history = store.getHistory(sessionId, 4);

        // Étape 4 — Construction du prompt
        String prompt = buildPrompt(userMessage, langueInstr, ragContext, webContext, history);

        // Étape 5 — Appel à l'IA
        try {
            return callOllama(prompt);
        } catch (Exception e) {
            log.warning("Ollama error: " + e.getMessage());
            return "Erreur technique. Appelez le 080 200 80 80.";
        }
    }

    private String buildPrompt(String question, String langueInstr,
                                String ragContext, String webContext, 
                                java.util.List<ma.bcp.assistant.model.ConversationLog> history) {
        StringBuilder ctx = new StringBuilder();
        if (!ragContext.isBlank())
            ctx.append("=== DOCUMENTS BCP ===\n").append(ragContext).append("\n\n");
        if (!webContext.isBlank())
            ctx.append("=== WEB ===\n").append(webContext).append("\n\n");
        ctx.append("=== INFOS GÉNÉRALES ===\n").append(BCP_KNOWLEDGE).append("\n\n");

        // On injecte la mémoire dans le prompt
        if (history != null && !history.isEmpty()) {
            ctx.append("=== HISTORIQUE DE LA CONVERSATION ===\n");
            for (var log : history) {
                ctx.append("Client: ").append(log.userMessage).append("\n");
                ctx.append("Toi: ").append(log.botResponse).append("\n");
            }
            ctx.append("\n");
        }

        return """
            Tu es un conseiller expert de la Banque Populaire du Maroc (BCP). %s

            REGLES :
            - Reponds TOUJOURS dans la langue demandee, sans exception
            - Sois precis, professionnel et utile, max 5 phrases claires
            - Utilise les infos BCP du contexte en priorite
            - Pour un credit, donne les taux et conditions precis
            - Si tu ne sais pas, oriente vers le 080 200 80 80
            - Tiens compte de lhistorique pour eviter les repetitions
            - Jamais de markdown (pas de *, **, #)

            === CONTEXTE ===
            %s

            === QUESTION DU CLIENT ===
            %s

            === REPONSE DU CONSEILLER BCP ===
            """.formatted(langueInstr, ctx.toString(), question);
    }

    private String callOllama(String prompt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var requestBody = mapper.writeValueAsString(java.util.Map.of(
            "model", model, "prompt", prompt, "stream", false,
            "options", java.util.Map.of("temperature", 0.1, "num_ctx", 1024)
        ));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/generate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new RuntimeException("Ollama: " + response.statusCode());
        return mapper.readTree(response.body()).get("response").asText().trim();
    }
}
