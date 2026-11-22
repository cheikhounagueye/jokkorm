plugins {
    id("jokkorm.spring-boot-service")
}

description = "rm-service: Records Management physical archive module. Slave of a SAE master (Maarch RM, Alfresco, Nuxeo, generic REST, or standalone)."

dependencies {
    // Phase 3B — Maarch REST adapter uses Spring's RestClient (built on Spring 3.5).
    // No extra dep needed; RestClient is in spring-web already pulled by starter-web.

    // Phase 3B — WireMock pour les tests d'adapter HTTP (Maarch).
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")

    // Phase 5L — Génération d'étiquettes code-barres (Code128, QR) rendues en PNG.
    // ZXing core = encodage, javase = rendu BufferedImage via java.awt (headless OK).
    // Même versions que dans eversuite-modern (ADR SAE.P1.6.B QR fantômes de prêt).
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // Phase 5M.2 — PDFBox pour générer les feuilles d'étiquettes Avery A4
    // (BatchLabelsPdfGenerator : grille N étiquettes × M pages selon le template).
    implementation("org.apache.pdfbox:pdfbox:3.0.4")

    // Phase 5I — Apache POI pour parser/générer les fichiers Excel d'import
    // topographie (modèle à télécharger + upload utilisateur). poi-ooxml pour XLSX.
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
}
