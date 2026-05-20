package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import ma.bcp.assistant.model.ConversationLog;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ConversationStore — Persistance PostgreSQL des conversations
 *
 * Remplace l'ancien stockage en mémoire par une vraie DB.
 * Utilisé dans ChatResource pour logger chaque échange.
 */
@ApplicationScoped
public class ConversationStore {

    private static final Logger log = Logger.getLogger(ConversationStore.class.getName());

    // Compteur global en mémoire (rapide, remis à 0 au redémarrage)
    public static final AtomicLong totalQueries = new AtomicLong(0);

    @Inject
    EntityManager em;

    // ─────────────────────────────────────────────────────────
    // Ajouter un log de conversation (Méthode statique legacy)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public static void addLog(String sessionId, String userMessage, String botResponse,
                              String status, String langue) {
        try {
            io.quarkus.arc.Arc.container()
                .instance(ConversationStore.class)
                .get()
                .saveLog(sessionId, userMessage, botResponse, status, langue);
        } catch (Exception e) {
            log.warning("[ConversationStore] Erreur sauvegarde : " + e.getMessage());
        }
        totalQueries.incrementAndGet();
    }

    // ─────────────────────────────────────────────────────────
    // Sauvegarde réelle (méthode d'instance avec transaction)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void saveLog(String sessionId, String userMessage, String botResponse,
                        String status, String langue) {
        ConversationLog logEntry = new ConversationLog(
            sessionId, userMessage, botResponse, status, langue
        );
        em.persist(logEntry);
        log.fine("[ConversationStore] Log sauvegardé pour session : " + sessionId);
    }

    // ─────────────────────────────────────────────────────────
    // NOUVEAU : Récupérer l'historique d'une session spécifique
    // ─────────────────────────────────────────────────────────
    public List<ConversationLog> getHistory(String sessionId, int limit) {
        return em.createQuery(
            "SELECT c FROM ConversationLog c WHERE c.sessionId = :sessionId ORDER BY c.createdAt ASC",
            ConversationLog.class
        ).setParameter("sessionId", sessionId)
         .setMaxResults(limit)
         .getResultList();
    }

    // ─────────────────────────────────────────────────────────
    // Récupérer les N dernières conversations (Global)
    // ─────────────────────────────────────────────────────────
    public List<ConversationLog> getLast(int limit) {
        return em.createQuery(
            "SELECT c FROM ConversationLog c ORDER BY c.createdAt DESC",
            ConversationLog.class
        ).setMaxResults(limit).getResultList();
    }

    // ─────────────────────────────────────────────────────────
    // Statistiques pour le dashboard admin
    // ─────────────────────────────────────────────────────────
    public long countTotal() {
        return em.createQuery(
            "SELECT COUNT(c) FROM ConversationLog c", Long.class
        ).getSingleResult();
    }

    public long countByStatus(String status) {
        return em.createQuery(
            "SELECT COUNT(c) FROM ConversationLog c WHERE c.status = :status",
            Long.class
        ).setParameter("status", status).getSingleResult();
    }

    public long countByLangue(String langue) {
        return em.createQuery(
            "SELECT COUNT(c) FROM ConversationLog c WHERE c.langue = :langue",
            Long.class
        ).setParameter("langue", langue).getSingleResult();
    }
}