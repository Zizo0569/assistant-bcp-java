package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SecurityService — équivalent de BCPSecurity dans main.py
 *
 * Filtre les messages dangereux avant de les envoyer au LLM.
 * Même logique de patterns que security.py / threat_patterns.py
 */
@ApplicationScoped
public class SecurityService {

    // Patterns dangereux — même logique que threat_patterns.py
    private static final List<Pattern> DANGER_PATTERNS = List.of(
        Pattern.compile("(?i)ignore.*(instruction|prompt|système|system)"),
        Pattern.compile("(?i)jailbreak|DAN|do anything now"),
        Pattern.compile("(?i)oublie.*(règle|instruction|contexte)"),
        Pattern.compile("(?i)pretend.*(you are|tu es).*(hacker|criminel|sans restriction)"),
        Pattern.compile("(?i)(mot de passe|password|mdp).*(admin|root|system)"),
        Pattern.compile("(?i)injection.*(sql|code|script)"),
        Pattern.compile("<script[^>]*>"),
        Pattern.compile("(?i)bomb|explos|terroris|arme"),
        Pattern.compile("(?i)hack.*(banque|système|serveur|bcp)")
    );

    // Triggers escalade vers conseiller humain — même que ESCALADE_TRIGGERS
    private static final List<String> ESCALADE_TRIGGERS = List.of(
        "parler à un conseiller", "conseiller humain", "agent humain",
        "parler à quelqu'un", "numéro de téléphone", "rendez-vous",
        "réclamation", "plainte", "arnaque", "fraude", "urgent"
    );

    // ──────────────────────────────────────────────────────
    // Analyser un message — retourne safe=false si dangereux
    // Équivalent de BCPSecurity.analyze() dans main.py
    // ──────────────────────────────────────────────────────
    public AnalysisResult analyze(String message) {
        if (message == null || message.isBlank()) {
            return new AnalysisResult(false, message, "Message vide");
        }

        // Vérifier patterns dangereux
        for (Pattern p : DANGER_PATTERNS) {
            if (p.matcher(message).find()) {
                return new AnalysisResult(false, message, "Pattern dangereux détecté");
            }
        }

        // Nettoyer le texte (enlever balises HTML basiques)
        String clean = message
            .replaceAll("<[^>]+>", "")
            .replaceAll("\\s+", " ")
            .trim();

        return new AnalysisResult(true, clean, null);
    }

    // ──────────────────────────────────────────────────────
    // Vérifier si le message déclenche une escalade humaine
    // ──────────────────────────────────────────────────────
    public boolean isEscaladeMessage(String message) {
        String lower = message.toLowerCase();
        return ESCALADE_TRIGGERS.stream().anyMatch(lower::contains);
    }

    public String getEscaladeReponse() {
        return "Je comprends votre demande. Un conseiller BCP est disponible :\n" +
               "📞 080 200 80 80 (gratuit, 24h/24)\n" +
               "Ou en agence — plus de 1 300 agences partout au Maroc.";
    }

    // ──────────────────────────────────────────────────────
    // Résultat de l'analyse
    // ──────────────────────────────────────────────────────
    public static class AnalysisResult {
        public final boolean safe;
        public final String  cleanText;
        public final String  reason;

        public AnalysisResult(boolean safe, String cleanText, String reason) {
            this.safe      = safe;
            this.cleanText = cleanText;
            this.reason    = reason;
        }
    }
}
