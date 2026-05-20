package ma.bcp.assistant.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ma.bcp.assistant.service.ConversationStore;
import ma.bcp.assistant.service.OllamaService;
import ma.bcp.assistant.service.OnboardingService;
import ma.bcp.assistant.service.SecurityService;

import java.util.Map;
import java.util.logging.Logger;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    private static final Logger log = Logger.getLogger(ChatResource.class.getName());

    @Inject OnboardingService onboarding;
    @Inject OllamaService     ollama;
    @Inject SecurityService   security;
    @Inject ConversationStore store;

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status",  "OK",
            "service", "BCP Quarkus",
            "port",    8081
        )).build();
    }

    @POST
    @Path("/chat")
    public Response chat(ChatRequest req) {
        if (req == null || req.message == null || req.message.isBlank())
            return Response.status(422).entity(Map.of("reponse", "Message vide.")).build();

        String sessionId = (req.session_id != null && !req.session_id.isBlank())
                         ? req.session_id : "session_default";
        String message = req.message.trim();
        String langue  = req.langue != null ? req.langue : "fr";

        if (security.isEscaladeMessage(message)) {
            String rep = security.getEscaladeReponse();
            store.saveLog(sessionId, message, rep, "OK", langue);
            return Response.ok(Map.of("reponse", rep)).build();
        }

        String etapeCourante = onboarding.getOrCreate(sessionId).etape;
        boolean bypass = etapeCourante != null
                      && OnboardingService.ETAPES_BYPASS_SECURITE.contains(etapeCourante);

        String cleanMessage = message;
        if (!bypass) {
            var analysis = security.analyze(message);
            if (!analysis.safe) {
                log.warning("[SECURITE] Bloque: " + analysis.reason);
                store.saveLog(sessionId, message, "Bloque", "DANGER", langue);
                return Response.ok(Map.of("reponse",
                    "Requete bloquee pour des raisons de securite.")).build();
            }
            cleanMessage = analysis.cleanText;
        }

        var obResult = onboarding.process(sessionId, message, langue);
        if (obResult != null) {
            var state = obResult.state;
            store.saveLog(sessionId, message, obResult.reponse, "OK", langue);
            return Response.ok(Map.of(
                "reponse",    obResult.reponse,
                "onboarding", true,
                "etape",      state.etape  != null ? state.etape  : "",
                "termine",    state.termine,
                "prenom",     state.prenom != null ? state.prenom : ""
            )).build();
        }

        String botReply = ollama.ask(sessionId, cleanMessage, langue);
        store.saveLog(sessionId, message, botReply, "OK", langue);
        return Response.ok(Map.of("reponse", botReply)).build();
    }
}

class ChatRequest {
    public String message    = "";
    public String session_id = "session_default";
    public String langue     = "fr";
}