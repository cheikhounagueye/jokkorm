package com.jokkorm.domain.master;

import java.util.List;
import java.util.Objects;

/**
 * Identité d'un agent telle que connue du maître. Un même {@code subject} SSO doit
 * renvoyer la même identité aux deux côtés — c'est la ligne « Identité, rôles » du
 * tableau de répartition (les deux, partagé via SSO).
 *
 * @param subject identifiant stable du compte (claim OIDC {@code sub})
 * @param displayName nom humain (jamais une adresse e-mail en clair dans le journal)
 * @param roles  rôles portés côté maître — pour contrôle croisé en audit
 */
public record AgentIdentity(String subject, String displayName, List<String> roles) {

    public AgentIdentity {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(displayName, "displayName");
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
