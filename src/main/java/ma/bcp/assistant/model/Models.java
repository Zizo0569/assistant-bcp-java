package ma.bcp.assistant.model;

// ══════════════════════════════════════════════════════════════
// ChatRequest — équivalent de class Question(BaseModel) dans main.py
//   message:    str
//   session_id: str = "session_default"
//   langue:     str = "fr"
// ══════════════════════════════════════════════════════════════
class ChatRequest {
    public String message    = "";
    public String session_id = "session_default";
    public String langue     = "fr";   // "fr", "ar", "da" (darija)
}

// ══════════════════════════════════════════════════════════════
// ChatResponse — retourné par /chat
// Champs optionnels : onboarding, etape, termine, prenom
// (même structure JSON que Python pour que le front ne change pas)
// ══════════════════════════════════════════════════════════════
class ChatResponse {
    public String  reponse;
    public Boolean onboarding = false;
    public String  etape      = null;
    public Boolean termine    = false;
    public String  prenom     = null;

    public ChatResponse(String reponse) {
        this.reponse = reponse;
    }
    public ChatResponse onboarding(String etape, boolean termine, String prenom) {
        this.onboarding = true;
        this.etape      = etape;
        this.termine    = termine;
        this.prenom     = prenom;
        return this;
    }
}

// ══════════════════════════════════════════════════════════════
// SimulationCredit — équivalent de class SimulationCredit(BaseModel)
//   capital:     float
//   taux_annuel: float
//   duree_ans:   int
// ══════════════════════════════════════════════════════════════
class SimulationCreditRequest {
    public double capital;
    public double taux_annuel;
    public int    duree_ans;
}

// ══════════════════════════════════════════════════════════════
// SimulationCreditResponse — même JSON que Python
// ══════════════════════════════════════════════════════════════
class SimulationCreditResponse {
    public String statut;
    public String capital;
    public String taux_annuel;
    public String duree;
    public String mensualite;
    public String cout_total;
    public String cout_interets;
}

// ══════════════════════════════════════════════════════════════
// Feedback — équivalent de class Feedback(BaseModel)
//   session_id: str
//   message:    str
//   reponse:    str
//   note:       int
// ══════════════════════════════════════════════════════════════
class FeedbackRequest {
    public String session_id;
    public String message;
    public String reponse;
    public int    note;
}

// ══════════════════════════════════════════════════════════════
// Requête vers Ollama (format API Ollama)
// ══════════════════════════════════════════════════════════════
class OllamaRequest {
    public String  model;
    public String  prompt;
    public boolean stream = false;
    public OllamaOptions options;

    public OllamaRequest(String model, String prompt) {
        this.model   = model;
        this.prompt  = prompt;
        this.options = new OllamaOptions();
    }

    public static class OllamaOptions {
        public double temperature = 0.1;
        public int    num_ctx     = 2048;
    }
}

// ══════════════════════════════════════════════════════════════
// Réponse d'Ollama
// ══════════════════════════════════════════════════════════════
class OllamaResponse {
    public String response;  // texte généré
    public boolean done;
}
