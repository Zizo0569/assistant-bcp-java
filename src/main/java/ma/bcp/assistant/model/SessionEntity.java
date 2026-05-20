package ma.bcp.assistant.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * SessionEntity — stocke les sessions onboarding dans PostgreSQL
 * Remplace le ConcurrentHashMap de OnboardingService
 */
@Entity
@Table(name = "sessions_java")
public class SessionEntity extends PanacheEntity {

    @Column(name = "session_id", unique = true, nullable = false)
    public String sessionId;

    @Column(name = "data", columnDefinition = "TEXT")
    public String data;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    // ── Requêtes Panache ──
    public static SessionEntity findBySessionId(String sessionId) {
        return find("sessionId", sessionId).firstResult();
    }

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = OffsetDateTime.now();
    }
}