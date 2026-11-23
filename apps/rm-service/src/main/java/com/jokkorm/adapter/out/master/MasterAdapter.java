package com.jokkorm.adapter.out.master;

import com.jokkorm.domain.master.SaeMasterPort;

/**
 * Un adapter de maître concret, identifié par son {@link #kind()} : c'est la valeur
 * qu'on écrit dans {@code jokkorm.master.kind} ou {@code jokkorm.master.tenants.<tenant>}.
 * Le {@link RoutingSaeMasterPort} choisit l'adapter de chaque tenant.
 */
public interface MasterAdapter extends SaeMasterPort {

    /** Identifiant de configuration : noop, demo, maarch, maarchrm… */
    String kind();

    /** Nom montré dans l'interface (« Maarch RM », « Maître de démonstration »…). */
    String displayName();
}
