package ma.bcp.assistant.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ConversationLog — Entité JPA persistée dans PostgreSQL
 * Table : conversation_logs
 */
@Entity
@Table(name = "conversation_logs")
public class ConversationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // ── NOUVEAU CHAMP : Pour lier le message à une session précise ──
    @Column(name = "session_id", length = 100)
    public String sessionId;

    @Column(name = "user_message", columnDefinition = "TEXT")
    public String userMessage;

    @Column(name = "bot_response", columnDefinition = "TEXT")
    public String botResponse;

    @Column(name = "status", length = 20)
    public String status = "OK";

    @Column(name = "langue", length = 10)
    public String langue = "fr";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    // ── Constructeurs ────────────────────────────────────────
    public ConversationLog() {}

    // Mise à jour du constructeur pour inclure sessionId
    public ConversationLog(String sessionId, String userMessage, String botResponse,
                           String status, String langue) {
        this.sessionId   = sessionId;
        this.userMessage = userMessage;
        this.botResponse = botResponse;
        this.status      = status;
        this.langue      = langue;
        this.createdAt   = LocalDateTime.now();
    }
}