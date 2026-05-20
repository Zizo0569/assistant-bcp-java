package ma.bcp.assistant.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
// ══════════════════════════════════════════════════════════════════════════════
// ConversationStore — store statique partagé (logs en mémoire)
// Utilisé directement via ConversationStore.addLog() — pas d'injection CDI
// ══════════════════════════════════════════════════════════════════════════════
class ConversationStore {

    public static final List<Map<String, Object>> logs = new CopyOnWriteArrayList<>();

    // ✅ AtomicInteger pour thread-safety
    public static final AtomicInteger totalQueries  = new AtomicInteger(0);
    public static final AtomicInteger dangerAlerts  = new AtomicInteger(0);
    public static final AtomicInteger warningAlerts = new AtomicInteger(0);
    public static final AtomicInteger messagesOk    = new AtomicInteger(0);
    public static final AtomicInteger messagesBloques = new AtomicInteger(0);

    public static void addLog(String question, String reponse, String status, String langue) {
        totalQueries.incrementAndGet();

        switch (status != null ? status : "OK") {
            case "DANGER"  -> { dangerAlerts.incrementAndGet();   messagesBloques.incrementAndGet(); }
            case "WARNING" -> warningAlerts.incrementAndGet();
            default        -> messagesOk.incrementAndGet();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("question",  truncate(question, 100));
        entry.put("message",   truncate(question, 100));
        entry.put("reponse",   truncate(reponse,  200));
        entry.put("status",    status   != null ? status : "OK");
        entry.put("langue",    langue   != null ? langue : "fr");
        entry.put("timestamp", new Date().toString());

        logs.add(entry);
        if (logs.size() > 200) logs.remove(0);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FeedbackResource — POST /feedback
// ══════════════════════════════════════════════════════════════════════════════
@Path("/feedback")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FeedbackResource {

    private static final Logger log = Logger.getLogger(FeedbackResource.class.getName());
    private static final List<Map<String, Object>> feedbacks = new CopyOnWriteArrayList<>();

    @POST
    public Response saveFeedback(FeedbackRequest req) {
        if (req == null) return Response.status(422).build();
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("session_id", req.session_id);
        fb.put("message",    req.message);
        fb.put("reponse",    req.reponse);
        fb.put("note",       req.note);
        fb.put("timestamp",  new Date().toString());
        feedbacks.add(fb);
        if (feedbacks.size() > 100) feedbacks.remove(0);
        log.info("[FEEDBACK] note=" + req.note);
        return Response.ok(Map.of("ok", true)).build();
    }

    @GET
    public Response getFeedbacks(@QueryParam("key") String key,
                                 @HeaderParam("X-Admin-Key") String keyHeader) {
        return Response.ok(Map.of("feedbacks", feedbacks)).build();
    }
}

class FeedbackRequest {
    public String session_id;
    public String message;
    public String reponse;
    public String note;
}

// ══════════════════════════════════════════════════════════════════════════════
// AdminResource — GET /admin/dashboard
// ══════════════════════════════════════════════════════════════════════════════
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
class AdminResource {

    @Inject
    jakarta.persistence.EntityManager em;

    @ConfigProperty(name = "bcp.admin.key", defaultValue = "Zidane2026")
    String adminKey;

    @GET
    @Path("/dashboard")
    public Response dashboard(@QueryParam("key") String keyParam,
                              @HeaderParam("X-Admin-Key") String keyHeader) {
        String key = keyParam != null ? keyParam : keyHeader;
        if (!adminKey.equals(key))
            return Response.status(401).entity(Map.of("error", "Acces refuse.")).build();

        // ── Stats depuis PostgreSQL ──
        long total   = (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c").getSingleResult();
        long danger  = (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.status = 'DANGER'").getSingleResult();
        long ok      = (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.status = 'OK'").getSingleResult();
        long bloques = (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.status != 'OK'").getSingleResult();

        // ── Comptage par langue ──
        Map<String, Long> langCount = new LinkedHashMap<>();
        langCount.put("fr", (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.langue = 'fr'").getSingleResult());
        langCount.put("ar", (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.langue = 'ar'").getSingleResult());
        langCount.put("da", (long) em.createQuery("SELECT COUNT(c) FROM ConversationLog c WHERE c.langue = 'da'").getSingleResult());

        // ── Sessions actives ──
        long sessions = (long) em.createQuery("SELECT COUNT(s) FROM SessionEntity s WHERE s.data LIKE '%\"termine\":false%'").getSingleResult();

        // ── Derniers logs ──
        var logs = em.createQuery(
            "SELECT c FROM ConversationLog c ORDER BY c.createdAt DESC",
            ma.bcp.assistant.model.ConversationLog.class)
            .setMaxResults(50)
            .getResultList();

        var logsMap = logs.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        c.id);
            m.put("message",   c.userMessage != null ? c.userMessage.substring(0, Math.min(80, c.userMessage.length())) : "");
            m.put("reponse",   c.botResponse != null ? c.botResponse.substring(0, Math.min(150, c.botResponse.length())) : "");
            m.put("status",    c.status);
            m.put("langue",    c.langue);
            m.put("timestamp", c.createdAt != null ? c.createdAt.toString() : "");
            return m;
        }).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_queries",    total);
        stats.put("total_messages",   total);
        stats.put("danger_alerts",    danger);
        stats.put("warning_alerts",   0);
        stats.put("messages_ok",      ok);
        stats.put("messages_bloques", bloques);
        stats.put("sessions_actives", sessions);
        stats.put("fiabilite",        total > 0 ? (ok * 100 / total) : 100);
        stats.put("lang_count",       langCount);

        return Response.ok(Map.of("stats", stats, "logs", logsMap)).build();
    }

    @GET
    @Path("/logs")
    public Response getLogs(@QueryParam("key") String keyParam,
                            @HeaderParam("X-Admin-Key") String keyHeader) {
        String key = keyParam != null ? keyParam : keyHeader;
        if (!adminKey.equals(key))
            return Response.status(401).entity(Map.of("error", "Acces refuse.")).build();

        var logs = em.createQuery(
            "SELECT c FROM ConversationLog c ORDER BY c.createdAt DESC",
            ma.bcp.assistant.model.ConversationLog.class)
            .setMaxResults(100)
            .getResultList();

        return Response.ok(Map.of("logs", logs)).build();
    }
}