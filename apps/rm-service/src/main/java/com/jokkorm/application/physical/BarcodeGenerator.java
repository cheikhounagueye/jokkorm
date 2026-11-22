package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Génère un code-barres unique pour une nouvelle boîte physique.
 *
 * <p>Format : {@code BOX-YYMMDD-XXXXXX} où {@code XXXXXX} sont 6 caractères Crockford
 * base32 (alphabet {@code 0-9A-HJKMNP-TV-Z} — pas de O/L/I/U pour éviter les confusions
 * visuelles sur une étiquette imprimée).
 *
 * <ul>
 *   <li>Longueur fixe = 18 caractères, imprimable petit sans scanner s'épuiser.</li>
 *   <li>Triable chronologiquement grâce au préfixe {@code YYMMDD}.</li>
 *   <li>Charset Code128 / Code39 compatible pour toute douchette standard.</li>
 *   <li>Unicité garantie par vérification + retry : 32^6 ≈ 1 milliard de combinaisons par
 *       jour, la probabilité de collision est négligeable. On retry max 10 fois avant d'abandonner.</li>
 * </ul>
 *
 * <p>Le préfixe {@code BOX} est fixe pour les conteneurs physiques en phase 5K. D'autres
 * préfixes ({@code CTR} pour carton, {@code FLD} pour classeur, {@code TUB} pour tube à plans)
 * viendront quand on introduira des types de conteneurs distincts.
 */
@Service
public class BarcodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // Crockford base32, 32 chars
    private static final int RANDOM_LENGTH = 6;
    private static final int MAX_RETRIES = 10;

    private final PhysicalContainerRepository containers;
    private final Clock clock;
    private final SecureRandom random;

    public BarcodeGenerator(PhysicalContainerRepository containers, Clock clock) {
        this.containers = containers;
        this.clock = clock;
        this.random = new SecureRandom();
    }

    public String generateForContainer(TenantId tenant) {
        String datePart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String candidate = "BOX-" + datePart + "-" + randomSuffix();
            if (!containers.existsByBarcode(tenant, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "Could not generate a unique barcode after %d attempts — alphabet exhausted?"
                .formatted(MAX_RETRIES));
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
