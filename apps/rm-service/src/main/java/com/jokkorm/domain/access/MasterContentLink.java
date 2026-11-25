package com.jokkorm.domain.access;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Où retrouver une numérisation déposée dans la GED/SAE maître (pattern 07).
 *
 * <p>JokkoRM ne garde jamais le fichier numérisé : il vit chez le maître, qui en contrôle
 * l'accès, la conservation et l'élimination. JokkoRM ne garde que cette référence, saisie par
 * l'archiviste après son dépôt dans le maître, en attendant le connecteur d'envoi.
 *
 * @param reference identifiant de la numérisation chez le maître (n° d'archive, cote de
 *                  l'extrait…), obligatoire
 * @param url       lien vers la numérisation dans l'interface du maître, facultatif ; toujours
 *                  http(s), sans identifiant ni mot de passe
 */
public record MasterContentLink(String reference, String url) {

    public static final int MAX_REFERENCE = 200;
    public static final int MAX_URL = 2000;

    /**
     * @param requiredOrigin adresse publique du maître (ex. {@code https://maarch.example.org}) :
     *                       si elle est connue, le lien doit y pointer ; {@code null} = pas de
     *                       contrôle d'origine (maître de démonstration, sans interface)
     */
    public static MasterContentLink of(String reference, String url, String requiredOrigin) {
        return new MasterContentLink(checkReference(reference), checkUrl(url, requiredOrigin));
    }

    private static String checkReference(String raw) {
        String ref = raw == null ? "" : raw.strip();
        if (ref.isEmpty()) {
            throw new IllegalArgumentException(
                "Indiquez la référence de la numérisation dans Maarch RM (n° d'archive ou cote de l'extrait).");
        }
        if (ref.length() > MAX_REFERENCE) {
            throw new IllegalArgumentException(
                "Référence trop longue : " + MAX_REFERENCE + " caractères au plus.");
        }
        if (ref.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("La référence contient un caractère invisible : recopiez-la.");
        }
        return ref;
    }

    private static String checkUrl(String raw, String requiredOrigin) {
        if (raw == null || raw.isBlank()) return null;
        String url = raw.strip();
        if (url.length() > MAX_URL) {
            throw new IllegalArgumentException("Lien trop long : " + MAX_URL + " caractères au plus.");
        }
        URI uri = parse(url, "Lien invalide : copiez l'adresse complète affichée par Maarch RM.");
        if (!isHttp(uri) || uri.getHost() == null) {
            throw new IllegalArgumentException("Le lien doit commencer par https:// ou http://.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Le lien ne doit contenir ni identifiant ni mot de passe.");
        }
        if (requiredOrigin != null && !requiredOrigin.isBlank()) {
            URI origin = parse(requiredOrigin.strip(), "Adresse publique du maître mal configurée.");
            if (!sameOrigin(uri, origin) || !pathUnder(uri, origin)) {
                throw new IllegalArgumentException(
                    "Le lien doit pointer vers Maarch RM (" + requiredOrigin.strip() + ").");
            }
        }
        return url;
    }

    private static URI parse(String value, String message) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"));
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme() == null ? "" : b.getScheme())
            && a.getHost().equalsIgnoreCase(b.getHost() == null ? "" : b.getHost())
            && port(a) == port(b);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equals(uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT)) ? 443 : 80;
    }

    /** Maarch publié sous un sous-chemin (https://hote/maarch) : le lien doit rester dessous. */
    private static boolean pathUnder(URI link, URI origin) {
        String base = origin.getRawPath() == null ? "" : origin.getRawPath().replaceAll("/+$", "");
        if (base.isEmpty()) return true;
        String path = link.normalize().getRawPath(); // « /maarch/../autre » ne sort pas du sous-chemin
        if (path == null) path = "";
        return path.equals(base) || path.startsWith(base + "/");
    }
}
