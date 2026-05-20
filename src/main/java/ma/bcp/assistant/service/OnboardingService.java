package ma.bcp.assistant.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ma.bcp.assistant.model.SessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@ApplicationScoped
public class OnboardingService {

    private static final Logger log = Logger.getLogger(OnboardingService.class.getName());

    @Inject
    ObjectMapper mapper;

    private static final Set<String> TRIGGER_WORDS = Set.of(
        "ouvrir", "compte", "credit", "creer", "nouveau", "inscription",
        "client", "rejoindre", "souscrire", "carte", "enregistrer",
        "bcp", "banque", "pret", "financement", "bghit", "nftah", "n7el",
        "قرض", "حساب", "بطاقة", "تمويل", "بنك"
    );

    public static final Set<String> ETAPES_BYPASS_SECURITE = Set.of(
        "prenom", "age", "profil", "besoin", "emploi", "montant", "duree", "telephone"
    );

    // ── Messages multilingues ─────────────────────────────────────────────────
    private static final Map<String, Map<String, String>> MSG = Map.ofEntries(

        Map.entry("bienvenue", Map.of(
            "fr", "Bonjour ! Je suis l'Assistant de la Banque Populaire du Maroc.\nQuel est votre prenom ?",
            "ar", "مرحباً ! أنا مساعد بنك الشعبي للمغرب.\nما هو اسمك الأول ؟",
            "da", "السلام عليكم ! أنا مساعد البنك الشعبي ديال المغرب.\nشنو هو سميتك ؟"
        )),

        // ── PRENOM ──
        Map.entry("err_prenom", Map.of(
            "fr", "Prenom invalide. Veuillez entrer votre vrai prenom (au moins 2 lettres, sans chiffres).",
            "ar", "الاسم غير صالح. من فضلك أدخل اسمك الحقيقي (حرفان على الأقل، بدون أرقام).",
            "da", "السمية ماصحاش. عفاك دخل سميتك الحقيقية (حرفين على لأقل، بلا أرقام)."
        )),

        Map.entry("merci_prenom", Map.of(
            "fr", "Merci %s ! Quel est votre age ? (entre 18 et 90 ans)",
            "ar", "شكراً %s ! كم عمرك ؟ (بين 18 و 90 سنة)",
            "da", "شكراً %s ! شحال عمرك ؟ (بين 18 و 90 عام)"
        )),

        // ── AGE ──
        Map.entry("err_age", Map.of(
            "fr", "Age invalide. Veuillez entrer un age entre 18 et 90 ans (ex : 35).",
            "ar", "العمر غير صالح. من فضلك أدخل عمراً بين 18 و 90 سنة (مثال: 35).",
            "da", "السن ماصحاش. عفاك دخل سن بين 18 و 90 عام (مثلاً: 35)."
        )),

        Map.entry("quel_profil", Map.of(
            "fr", "Quel est votre profil ?\n• Particulier\n• Etudiant\n• Professionnel\n• Entreprise",
            "ar", "ما هو ملفك الشخصي ؟\n• فرد\n• طالب\n• مهني\n• شركة",
            "da", "شنو هو بروفيلك ؟\n• فرد\n• طالب\n• مهني\n• شركة"
        )),

        // ── PROFIL ──
        Map.entry("err_profil", Map.of(
            "fr", "Profil non reconnu. Choisissez parmi :\n• Particulier\n• Etudiant\n• Professionnel\n• Entreprise",
            "ar", "الملف الشخصي غير معروف. اختر من بين :\n• فرد\n• طالب\n• مهني\n• شركة",
            "da", "البروفيل ما عرفناهش. ختار من بين :\n• فرد\n• طالب\n• مهني\n• شركة"
        )),

        Map.entry("quel_besoin", Map.of(
            "fr", "Que recherchez-vous ?\n• Credit\n• Compte\n• Carte",
            "ar", "ماذا تبحث عن ؟\n• قرض\n• حساب\n• بطاقة",
            "da", "شنو كتقلب على ؟\n• قرض\n• حساب\n• كارطة"
        )),

        // ── BESOIN ──
        Map.entry("err_besoin", Map.of(
            "fr", "Choix non reconnu. Veuillez choisir parmi :\n• Credit\n• Compte\n• Carte",
            "ar", "الخيار غير معروف. من فضلك اختر من بين :\n• قرض\n• حساب\n• بطاقة",
            "da", "الاختيار ما عرفناهش. ختار من بين :\n• قرض\n• حساب\n• كارطة"
        )),

        Map.entry("quel_montant", Map.of(
            "fr", "Quel montant souhaitez-vous emprunter ? (ex : 200000 DH)",
            "ar", "ما هو المبلغ الذي تريد اقتراضه ؟ (مثال: 200000 درهم)",
            "da", "شحال بغيتي تسلف ؟ (مثلاً: 200000 درهم)"
        )),

        // ── MONTANT ──
        Map.entry("err_montant", Map.of(
            "fr", "Montant invalide. Veuillez entrer un montant numerique valide (ex : 150000).",
            "ar", "المبلغ غير صالح. من فضلك أدخل مبلغاً رقمياً صحيحاً (مثال: 150000).",
            "da", "المبلغ ماصحاش. عفاك دخل مبلغ بالأرقام (مثلاً: 150000)."
        )),

        Map.entry("quelle_duree", Map.of(
            "fr", "Sur quelle duree souhaitez-vous rembourser ? (ex : 10 ans, 15 ans, 20 ans)",
            "ar", "على كم سنة تريد السداد ؟ (مثال: 10 سنوات، 15 سنة، 20 سنة)",
            "da", "على شحال ديال السنين بغيتي تخلص ؟ (مثلاً: 10 سنين، 15، 20)"
        )),

        // ── DUREE ──
        Map.entry("err_duree", Map.of(
            "fr", "Duree invalide. Entrez une duree entre 1 et 30 ans (ex : 15 ans).",
            "ar", "المدة غير صالحة. أدخل مدة بين سنة و30 سنة (مثال: 15 سنة).",
            "da", "المدة ماصحاش. دخل مدة بين سنة و 30 سنة (مثلاً: 15 عام)."
        )),

        Map.entry("quelle_situation", Map.of(
            "fr", "Quelle est votre situation professionnelle ?\n• Salarie\n• Fonctionnaire\n• Independant\n• Sans emploi",
            "ar", "ما هو وضعك المهني ؟\n• موظف قطاع خاص\n• موظف حكومي\n• مستقل\n• بدون عمل",
            "da", "شنو هي وضعيتك المهنية ؟\n• موظف\n• خادم الدولة\n• مستقل\n• بلا خدمة"
        )),

        // ── EMPLOI ──
        Map.entry("err_emploi", Map.of(
            "fr", "Situation non reconnue. Choisissez parmi :\n• Salarie\n• Fonctionnaire\n• Independant\n• Sans emploi",
            "ar", "الوضع غير معروف. اختر من بين :\n• موظف قطاع خاص\n• موظف حكومي\n• مستقل\n• بدون عمل",
            "da", "الوضعية ما عرفناهاش. ختار من بين :\n• موظف\n• خادم الدولة\n• مستقل\n• بلا خدمة"
        )),

        Map.entry("quel_telephone", Map.of(
            "fr", "Votre numero de telephone pour qu'un conseiller BCP vous contacte ?\n(Format : 06XXXXXXXX ou 07XXXXXXXX)",
            "ar", "ما هو رقم هاتفك لكي يتواصل معك مستشار BCP ؟\n(الصيغة: 06XXXXXXXX أو 07XXXXXXXX)",
            "da", "شنو هو نمرة تيليفونك باش يتصل بيك المستشار ديال BCP ؟\n(بحال: 06XXXXXXXX)"
        )),

        // ── TELEPHONE ──
        Map.entry("err_tel", Map.of(
            "fr", "Numero invalide. Le numero doit commencer par 05, 06 ou 07 et contenir 10 chiffres.\nEx : 0612345678",
            "ar", "الرقم غير صالح. يجب أن يبدأ بـ 05 أو 06 أو 07 ويحتوي على 10 أرقام.\nمثال: 0612345678",
            "da", "النمرة غلط. خاصها تبدا بـ 05 أو 06 أو 07 و فيها 10 أرقام.\nمثلاً: 0612345678"
        )),

        // ── RECAP ──
        Map.entry("recap_header", Map.of(
            "fr", "Merci %s ! Votre dossier est bien enregistre.\n\n",
            "ar", "شكراً %s ! تم تسجيل ملفك بنجاح.\n\n",
            "da", "شكراً %s ! الدوسيي ديالك تسجل مزيان.\n\n"
        )),
        Map.entry("recap_footer", Map.of(
            "fr", "\nUn conseiller BCP vous contactera dans les 24h.\n📞 080 200 80 80",
            "ar", "\nسيتصل بك مستشار BCP خلال 24 ساعة.\n📞 080 200 80 80",
            "da", "\nغادي يتصل بيك مستشار BCP من داخل 24 ساعة.\n📞 080 200 80 80"
        )),
        Map.entry("recap_pdf", Map.of(
            "fr", "\n\n[📄 Telecharger votre recu PDF](%s)",
            "ar", "\n\n[📄 تحميل وصل PDF](%s)",
            "da", "\n\n[📄 حمل الوصل PDF](%s)"
        ))
    );

    // ── Validations ───────────────────────────────────────────────────────────

    private boolean isValidPrenom(String input) {
        if (input == null || input.trim().length() < 2) return false;
        // Accepte lettres latines, arabes, tirets et espaces — rejette les chiffres
        return input.trim().matches("[\\p{L}\\s'-]{2,40}");
    }

    private boolean isValidAge(String input) {
        try {
            int age = Integer.parseInt(input.trim().replaceAll("[^\\d]", ""));
            return age >= 18 && age <= 90;
        } catch (NumberFormatException e) { return false; }
    }

    private boolean isValidProfil(String input) {
        String m = input.trim().toLowerCase();
        return m.matches(".*(part|etud|pro|entre|societ|fard|talib|mhani|شركة|طالب|مهني|فرد).*");
    }

    private boolean isValidBesoin(String input) {
        String m = input.trim().toLowerCase();
        return m.matches(".*(credit|compte|carte|pret|قرض|حساب|بطاقة|كارط|تمويل).*");
    }

    private boolean isValidMontant(String input) {
        String digits = input.trim().replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return false;
        long montant = Long.parseLong(digits);
        return montant >= 1000 && montant <= 10_000_000;
    }

    private boolean isValidDuree(String input) {
        String digits = input.trim().replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return false;
        int duree = Integer.parseInt(digits);
        return duree >= 1 && duree <= 30;
    }

    private boolean isValidEmploi(String input) {
        String m = input.trim().toLowerCase();
        return m.matches(".*(salari|fonction|independant|sans|employ|موظف|مستقل|بدون|خادم|bla).*");
    }

    private boolean isValidPhone(String input) {
        String clean = input.trim().replaceAll("[\\s\\-\\.]", "");
        return Pattern.matches("^(05|06|07)\\d{8}$", clean);
    }

    // ── Machine à états ───────────────────────────────────────────────────────

    @Transactional
    public OnboardingResult process(String sessionId, String message, String langue) {
        SessionState s = getOrCreate(sessionId);
        String msgRaw   = message.trim();
        String msgLower = msgRaw.toLowerCase()
            .replace("\u00e9","e").replace("\u00e8","e").replace("\u00ea","e")
            .replace("\u00e0","a").replace("\u00e2","a").replace("\u00e7","c")
            .replace("\u00f4","o").replace("\u00f9","u").replace("\u00fb","u");

        // Déclenchement
        if (!s.actif && !s.termine) {
            if (TRIGGER_WORDS.stream().anyMatch(msgLower::contains)) {
                s.actif = true;
                s.etape = "prenom";
                saveSession(sessionId, s);
                return new OnboardingResult(t("bienvenue", langue), s);
            }
            return null;
        }

        if (s.termine || !s.actif) return null;

        return switch (s.etape) {

            // ── PRENOM ──────────────────────────────────────────────────────
            case "prenom" -> {
                if (!isValidPrenom(msgRaw)) {
                    yield new OnboardingResult(t("err_prenom", langue), s);
                }
                s.prenom = capitalize(msgRaw);
                s.etape  = "age";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("merci_prenom", langue, s.prenom), s);
            }

            // ── AGE ─────────────────────────────────────────────────────────
            case "age" -> {
                if (!isValidAge(msgRaw)) {
                    yield new OnboardingResult(t("err_age", langue), s);
                }
                s.age   = msgRaw.trim().replaceAll("[^\\d]", "");
                s.etape = "profil";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("quel_profil", langue), s);
            }

            // ── PROFIL ──────────────────────────────────────────────────────
            case "profil" -> {
                if (!isValidProfil(msgLower)) {
                    yield new OnboardingResult(t("err_profil", langue), s);
                }
                s.profil = capitalize(msgRaw);
                s.etape  = "besoin";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("quel_besoin", langue), s);
            }

            // ── BESOIN ──────────────────────────────────────────────────────
            case "besoin" -> {
                if (!isValidBesoin(msgLower)) {
                    yield new OnboardingResult(t("err_besoin", langue), s);
                }
                s.besoin = capitalize(msgRaw);
                boolean needsCredit = msgLower.matches(
                    ".*(credit|pret|immob|auto|financement|emprunt|loan|قرض|تمويل).*");
                s.etape = needsCredit ? "montant" : "emploi";
                saveSession(sessionId, s);
                yield new OnboardingResult(
                    needsCredit ? t("quel_montant", langue) : t("quelle_situation", langue), s);
            }

            // ── MONTANT ─────────────────────────────────────────────────────
            case "montant" -> {
                if (!isValidMontant(msgRaw)) {
                    yield new OnboardingResult(t("err_montant", langue), s);
                }
                s.montant = msgRaw.trim().replaceAll("[^\\d]", "") + " DH";
                s.etape   = "duree";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("quelle_duree", langue), s);
            }

            // ── DUREE ───────────────────────────────────────────────────────
            case "duree" -> {
                if (!isValidDuree(msgRaw)) {
                    yield new OnboardingResult(t("err_duree", langue), s);
                }
                s.duree = msgRaw.trim().replaceAll("[^\\d]", "") + " ans";
                s.etape = "emploi";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("quelle_situation", langue), s);
            }

            // ── EMPLOI ──────────────────────────────────────────────────────
            case "emploi" -> {
                if (!isValidEmploi(msgLower)) {
                    yield new OnboardingResult(t("err_emploi", langue), s);
                }
                s.emploi = capitalize(msgRaw);
                s.etape  = "telephone";
                saveSession(sessionId, s);
                yield new OnboardingResult(t("quel_telephone", langue), s);
            }

            // ── TELEPHONE ───────────────────────────────────────────────────
            case "telephone" -> {
                if (!isValidPhone(msgRaw)) {
                    yield new OnboardingResult(t("err_tel", langue), s);
                }
                s.telephone = msgRaw.trim().replaceAll("[\\s\\-\\.]", "");
                s.termine   = true;
                s.actif     = false;
                s.etape     = "termine";
                saveSession(sessionId, s);

                String lienPdf = "http://localhost:8081/telecharger-recu/" + sessionId;
                String recap   = t("recap_header", langue, s.prenom)
                    + "• Tel    : " + s.telephone + "\n"
                    + "• Besoin : " + s.besoin
                    + (s.montant != null ? "\n• Montant: " + s.montant : "")
                    + (s.duree   != null ? "\n• Duree  : " + s.duree   : "")
                    + t("recap_footer", langue)
                    + t("recap_pdf", langue, lienPdf);

                yield new OnboardingResult(recap, s);
            }

            default -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String t(String key, String langue) {
        Map<String, String> map = MSG.getOrDefault(key, Map.of());
        return map.getOrDefault(langue, map.getOrDefault("fr", "—"));
    }

    private String t(String key, String langue, Object... args) {
        return String.format(t(key, langue), args);
    }

    @Transactional
    public SessionState getOrCreate(String sessionId) {
        SessionEntity entity = SessionEntity.findBySessionId(sessionId);
        if (entity == null) {
            entity = new SessionEntity();
            entity.sessionId = sessionId;
            entity.data = toJson(new SessionState());
            entity.persist();
        }
        return fromJson(entity.data);
    }

    @Transactional
    public void saveSession(String sessionId, SessionState state) {
        SessionEntity entity = SessionEntity.findBySessionId(sessionId);
        if (entity == null) {
            entity = new SessionEntity();
            entity.sessionId = sessionId;
        }
        entity.data = toJson(state);
        entity.persist();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String toJson(SessionState s) {
        try { return mapper.writeValueAsString(s); }
        catch (Exception e) { return "{}"; }
    }

    private SessionState fromJson(String json) {
        try { return mapper.readValue(json, SessionState.class); }
        catch (Exception e) { return new SessionState(); }
    }

    // ── Compatibilité ChatResource (sans langue) ──────────────────────────────
    @Transactional
    public OnboardingResult process(String sessionId, String message) {
        return process(sessionId, message, "fr");
    }

    // ── Classes internes ──────────────────────────────────────────────────────
    public static class SessionState {
        public boolean actif     = false;
        public String  etape     = null;
        public boolean termine   = false;
        public String  prenom    = null;
        public String  age       = null;
        public String  profil    = null;
        public String  besoin    = null;
        public String  montant   = null;
        public String  duree     = null;
        public String  emploi    = null;
        public String  telephone = null;
    }

    public static class OnboardingResult {
        public final String       reponse;
        public final SessionState state;
        public OnboardingResult(String reponse, SessionState state) {
            this.reponse = reponse;
            this.state   = state;
        }
    }
}