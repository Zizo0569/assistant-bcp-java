package ma.bcp.assistant.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import ma.bcp.assistant.model.SessionEntity;
import ma.bcp.assistant.service.OnboardingService.SessionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

@Path("/telecharger-recu")
@Produces("application/pdf")
public class PdfResource {

    private static final Logger log = Logger.getLogger(PdfResource.class.getName());

    @Inject ObjectMapper mapper;

    private static final String RECUS_DIR = "C:/dev_stage/recus_bcp/";

    @GET
    @Path("/{sessionId}")
    public Response telechargerRecu(@PathParam("sessionId") String sessionId) {
        try {
            SessionEntity entity = SessionEntity.findBySessionId(sessionId);
            if (entity == null) {
                return Response.status(404)
                    .entity("Dossier introuvable.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
            }

            SessionState s = mapper.readValue(entity.data, SessionState.class);
            String dateStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy a HH:mm"));
            String ref = sessionId.substring(Math.max(0, sessionId.length() - 8));

            String html = buildHtml(s, ref, dateStr);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();

            byte[] pdfBytes = out.toByteArray();

            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(RECUS_DIR));
            String filename = "fiche_recapitulatif_bcp_" + ref + ".pdf";
            java.nio.file.Files.write(java.nio.file.Paths.get(RECUS_DIR + filename), pdfBytes);

            return Response.ok(pdfBytes)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Length", pdfBytes.length)
                .header("Access-Control-Allow-Origin", "*")
                .build();

        } catch (Exception e) {
            log.severe("Erreur PDF : " + e.getMessage());
            return Response.serverError()
                .entity("Erreur generation PDF : " + e.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
        }
    }

    private String buildHtml(SessionState s, String ref, String dateStr) {
        String prenom    = safe(s.prenom);
        String age       = safe(s.age);
        String profil    = safe(s.profil);
        String besoin    = safe(s.besoin);
        String emploi    = safe(s.emploi);
        String telephone = safe(s.telephone);

        // Chargement logo BCP en base64
        String logoTag = "<div class='header-logo-text'>BCP</div>";
        try {
            InputStream logoStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/logobcp.png");
            if (logoStream != null) {
                byte[] logoBytes = logoStream.readAllBytes();
                String b64 = java.util.Base64.getEncoder().encodeToString(logoBytes);
                logoTag = "<img src='data:image/png;base64," + b64
                        + "' style='height:42px; width:auto;'/>";
            }
        } catch (Exception e) {
            log.warning("Logo non charge : " + e.getMessage());
        }

        // Bloc simulation credit — uniquement si montant ET duree sont renseignes
        String simBlock = "";
        String montantRow = "";
        String dureeRow = "";

        if (s.montant != null && !s.montant.isBlank()
            && s.duree != null && !s.duree.isBlank()) {
            try {
                double capital  = Double.parseDouble(s.montant.replaceAll("[^\\d.]", ""));
                int    dureeAns = Integer.parseInt(s.duree.replaceAll("[^\\d]", ""));
                int    dureeMois = dureeAns * 12;
                double taux = 5.41 / 100 / 12;
                double mens  = (capital * taux) / (1 - Math.pow(1 + taux, -dureeMois));
                double total = mens * dureeMois;
                double interets = total - capital;

                simBlock = String.format("""
                    <div class="sim-block">
                        <div class="sim-label">Mensualite estimee (taux BCP 5.41%%)</div>
                        <div class="sim-amount">%,.0f DH / mois</div>
                        <table class="sim-table">
                            <tr><td>Capital emprunte</td><td>%,.0f DH</td></tr>
                            <tr><td>Duree</td><td>%d ans (%d mois)</td></tr>
                            <tr><td>Cout total</td><td>%,.0f DH</td></tr>
                            <tr><td>Dont interets</td><td>%,.0f DH</td></tr>
                        </table>
                        <div class="sim-note">Estimation indicative — Taux definitif fixe par votre conseiller BCP</div>
                    </div>
                    """, mens, capital, dureeAns, dureeMois, total, interets);

                montantRow = "<tr class='alt'><td class='lbl'>Montant souhaite</td><td class='val'>" + s.montant + "</td></tr>";
                dureeRow   = "<tr><td class='lbl'>Duree</td><td class='val'>" + s.duree + "</td></tr>";

            } catch (Exception e) {
                montantRow = "<tr class='alt'><td class='lbl'>Montant souhaite</td><td class='val'>" + safe(s.montant) + "</td></tr>";
                dureeRow   = "<tr><td class='lbl'>Duree</td><td class='val'>" + safe(s.duree) + "</td></tr>";
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8"/>
            <style>
              * { margin: 0; padding: 0; box-sizing: border-box; }
              body {
                font-family: Arial, Helvetica, sans-serif;
                font-size: 12px;
                color: #1a1a1a;
                background: #fff;
              }

              /* ── HEADER ── */
              .header {
                background-color: #8B0A14;
                padding: 22px 32px 18px;
                color: white;
              }
              .header-top {
                display: flex;
                align-items: center;
                margin-bottom: 10px;
              }
              .header-logo-circle {
                width: 44px;
                height: 44px;
                background: white;
                border-radius: 50%%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin-right: 14px;
                flex-shrink: 0;
              }
              .header-logo-text {
                font-size: 11px;
                font-weight: bold;
                color: #8B0A14;
                text-align: center;
                line-height: 1.2;
              }
              .header-title {
                font-size: 18px;
                font-weight: bold;
                color: white;
                margin-bottom: 3px;
              }
              .header-sub {
                font-size: 10px;
                color: rgba(255,255,255,0.75);
              }
              .header-ref {
                font-size: 10px;
                color: rgba(255,255,255,0.6);
                margin-top: 6px;
                font-style: italic;
              }

              /* ── BARRE ORANGE ── */
              .orange-bar {
                background-color: #E8720C;
                height: 6px;
              }

              /* ── BODY ── */
              .body {
                padding: 28px 36px;
              }

              /* ── TITRE ── */
              .doc-title {
                font-size: 22px;
                font-weight: bold;
                color: #8B0A14;
                text-align: center;
                margin-bottom: 4px;
              }
              .doc-subtitle {
                font-size: 11px;
                color: #999;
                text-align: center;
                margin-bottom: 24px;
              }

              /* ── BLOC SIMULATION ── */
              .sim-block {
                background-color: #E8720C;
                color: white;
                border-radius: 8px;
                padding: 16px 20px;
                margin-bottom: 22px;
                text-align: center;
              }
              .sim-label  { font-size: 10px; opacity: 0.85; margin-bottom: 4px; }
              .sim-amount { font-size: 26px; font-weight: bold; margin-bottom: 12px; }
              .sim-table  { width: 100%%; border-collapse: collapse; font-size: 11px; margin-bottom: 8px; }
              .sim-table td { padding: 4px 10px; color: rgba(255,255,255,0.9); text-align: left; }
              .sim-table td:last-child { text-align: right; font-weight: bold; }
              .sim-table tr:nth-child(odd) td { background: rgba(0,0,0,0.12); }
              .sim-note { font-size: 9px; opacity: 0.7; margin-top: 6px; font-style: italic; }

              /* ── SECTIONS ── */
              .section-title {
                background-color: #8B0A14;
                color: white;
                font-size: 10px;
                font-weight: bold;
                padding: 7px 14px;
                margin: 18px 0 0;
                letter-spacing: 0.5px;
              }

              .info-table {
                width: 100%%;
                border-collapse: collapse;
                margin-bottom: 4px;
              }
              .info-table td {
                padding: 8px 12px;
                border-bottom: 1px solid #f0ece8;
                font-size: 11px;
              }
              .info-table .alt td { background-color: #faf7f5; }
              .lbl { color: #888; width: 150px; }
              .val { color: #1a1a1a; font-weight: bold; }

              /* ── ZONE SIGNATURE ── */
              .sig-area {
                margin-top: 24px;
                display: flex;
                gap: 20px;
              }
              .sig-box {
                flex: 1;
                border: 1px solid #8B0A14;
                border-radius: 4px;
                padding: 12px;
                min-height: 70px;
              }
              .sig-box-orange {
                flex: 1;
                border: 1px solid #E8720C;
                border-radius: 4px;
                padding: 12px;
                min-height: 70px;
              }
              .sig-label {
                font-size: 9px;
                font-weight: bold;
                color: #8B0A14;
                margin-bottom: 8px;
              }
              .sig-label-orange {
                font-size: 9px;
                font-weight: bold;
                color: #E8720C;
                margin-bottom: 8px;
              }
              .sig-content {
                font-size: 9px;
                color: #999;
                text-align: center;
                margin-top: 6px;
              }
              .sig-ref {
                font-size: 9px;
                font-weight: bold;
                color: #E8720C;
                text-align: center;
              }

              /* ── SEPARATEUR ── */
              .separator {
                border: none;
                border-top: 1px solid #E8720C;
                margin: 20px 0;
              }

              /* ── NOTE CONFIDENTIALITE ── */
              .confidential {
                font-size: 8px;
                color: #aaa;
                text-align: center;
                margin-top: 10px;
                font-style: italic;
              }

              /* ── FOOTER ── */
              .footer {
                background-color: #8B0A14;
                color: white;
                padding: 14px 32px;
                text-align: center;
                margin-top: 24px;
              }
              .footer-orange-bar {
                background-color: #E8720C;
                height: 4px;
              }
              .footer p {
                font-size: 10px;
                opacity: 0.85;
                margin: 3px 0;
              }
              .footer .footer-ref {
                font-size: 9px;
                opacity: 0.65;
                font-style: italic;
                margin-top: 4px;
              }
            </style>
            </head>
            <body>

            <!-- HEADER -->
            <div class="header">
              <div class="header-top">
                <div class="header-logo-circle">
                  %s
                </div>
                <div>
                  <div class="header-title">Banque Populaire du Maroc</div>
                  <div class="header-sub">GE-GM | Assistant Intelligent BCP</div>
                </div>
              </div>
              <div class="header-ref">Ref. BCP-%s | %s</div>
            </div>
            <div class="orange-bar"></div>

            <!-- BODY -->
            <div class="body">

              <div class="doc-title">Dossier de %s</div>
              <div class="doc-subtitle">Votre demande a ete enregistree avec succes</div>

              %s

              <!-- INFOS PERSONNELLES -->
              <div class="section-title">INFORMATIONS PERSONNELLES</div>
              <table class="info-table">
                <tr><td class="lbl">Prenom</td><td class="val">%s</td></tr>
                <tr class="alt"><td class="lbl">Age</td><td class="val">%s ans</td></tr>
                <tr><td class="lbl">Profil</td><td class="val">%s</td></tr>
                <tr class="alt"><td class="lbl">Telephone</td><td class="val">%s</td></tr>
              </table>

              <!-- DETAILS DEMANDE -->
              <div class="section-title">DETAILS DE LA DEMANDE</div>
              <table class="info-table">
                <tr><td class="lbl">Besoin</td><td class="val">%s</td></tr>
                %s
                %s
                <tr><td class="lbl">Situation professionnelle</td><td class="val">%s</td></tr>
              </table>

              <hr class="separator"/>

              <!-- ZONE SIGNATURE -->
              <div class="sig-area">
                <div class="sig-box">
                  <div class="sig-label">Cachet BCP :</div>
                  <div class="sig-content">Banque Populaire du Maroc<br/>Direction Transformation Digitale</div>
                </div>
                <div class="sig-box-orange">
                  <div class="sig-label-orange">Visa Direction Digitale :</div>
                  <div class="sig-content">Document genere automatiquement<br/>Assistant Intelligent BCP v4.1</div>
                  <div class="sig-ref">REF: BCP-%s</div>
                </div>
              </div>

              <div class="confidential">
                Document confidentiel — Usage interne BCP uniquement — Ne pas diffuser
              </div>

            </div>

            <!-- FOOTER -->
            <div class="footer-orange-bar"></div>
            <div class="footer">
              <p>Banque Populaire du Maroc | www.groupebcp.com</p>
              <p>080 200 80 80 (Gratuit 24h/24) | Plus de 1 300 agences au Maroc</p>
              <p class="footer-ref">Ref. BCP-%s | (c) 2026 BCP Direction Transformation Digitale</p>
            </div>

            </body>
            </html>
            """.formatted(
                   logoTag,        // ← AJOUTER en premier
                   ref, dateStr,
                   prenom,
                   simBlock,
                   prenom, age, profil, telephone,
                   besoin, montantRow, dureeRow, emploi,
                   ref,
                   ref
              );
    }

    private String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "—";
    }
}