package ma.bcp.assistant.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ma.bcp.assistant.service.CreditService;
import java.util.Map;

@Path("/simuler-credit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CreditResource {

    @Inject
    CreditService creditService;

    @POST
    public Response simuler(SimulationCreditRequest req) {
        if (req == null)
            return Response.status(422).entity(Map.of("error", "Données manquantes")).build();
        try {
            var result = creditService.simuler(req.capital, req.taux_annuel, req.duree_ans);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(422).entity(Map.of("error", e.getMessage())).build();
        }
    }
}

class SimulationCreditRequest {
    public double capital;
    public double taux_annuel;
    public int    duree_ans;
}