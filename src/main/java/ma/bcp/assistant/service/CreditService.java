package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * CreditService — équivalent de _calc_direct() + simuler_credit() dans main.py
 *
 * Formule identique à Python :
 *   m = capital * (taux_m * (1+taux_m)^n) / ((1+taux_m)^n - 1)
 */
@ApplicationScoped
public class CreditService {

    // ──────────────────────────────────────────────────────
    // Calcul mensualité — même formule que _calc_direct() Python
    // ──────────────────────────────────────────────────────
    public SimulationResult simuler(double capital, double tauxAnnuel, int dureeAns) {
        if (capital <= 0) throw new IllegalArgumentException("Capital doit être positif");
        if (tauxAnnuel <= 0) throw new IllegalArgumentException("Taux doit être positif");
        if (dureeAns <= 0 || dureeAns > 40) throw new IllegalArgumentException("Durée invalide (1-40 ans)");

        int    n      = dureeAns * 12;           // nombre de mois
        double tauxM  = tauxAnnuel / 100.0 / 12; // taux mensuel

        // Formule de mensualité (même que Python)
        double mensualite;
        if (tauxM == 0) {
            mensualite = capital / n;
        } else {
            double facteur = Math.pow(1 + tauxM, n);
            mensualite = capital * (tauxM * facteur) / (facteur - 1);
        }

        double coutTotal    = mensualite * n;
        double coutInterets = coutTotal - capital;

        return new SimulationResult(capital, tauxAnnuel, dureeAns, n,
            mensualite, coutTotal, coutInterets);
    }

    // ──────────────────────────────────────────────────────
    // Résultat formaté — même JSON que Python
    // ──────────────────────────────────────────────────────
    public static class SimulationResult {
        public final String statut        = "OK";
        public final String capital;
        public final String taux_annuel;
        public final String duree;
        public final String mensualite;
        public final String cout_total;
        public final String cout_interets;

        public SimulationResult(double cap, double taux, int ans, int mois,
                                double mens, double ct, double ci) {
            this.capital       = String.format("%,.0f DH", cap);
            this.taux_annuel   = String.format("%.1f%%", taux);
            this.duree         = String.format("%d ans (%d mois)", ans, mois);
            this.mensualite    = String.format("%,.2f DH/mois", mens);
            this.cout_total    = String.format("%,.2f DH", ct);
            this.cout_interets = String.format("%,.2f DH", ci);
        }
    }
}
