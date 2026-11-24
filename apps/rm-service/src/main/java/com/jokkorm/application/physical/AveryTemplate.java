package com.jokkorm.application.physical;

/**
 * Templates de feuilles d'étiquettes autocollantes Avery A4 — standards du marché
 * français et européen. Mesures en millimètres (converties en points PDF par le générateur).
 *
 * <p>Chaque template définit la grille (colonnes × lignes), la taille d'une étiquette,
 * et les marges autour (bords de la feuille + espace inter-étiquettes).
 */
public enum AveryTemplate {

    /** L7160 : 63.5 × 38.1 mm, 3 colonnes × 7 lignes = 21 étiquettes par A4. Boîtes moyennes. */
    AVERY_L7160("Avery L7160 · 21 étiquettes 63.5 × 38.1 mm",
        3, 7, 63.5, 38.1, 7.21, 15.1, 2.5, 0.0),

    /** L7159 : 63.5 × 33.9 mm, 3 colonnes × 8 lignes = 24 étiquettes par A4. Boîtes petites. */
    AVERY_L7159("Avery L7159 · 24 étiquettes 63.5 × 33.9 mm",
        3, 8, 63.5, 33.9, 7.21, 13.0, 2.5, 0.0),

    /**
     * L7162 : 99.1 × 33.9 mm, 2 colonnes × 8 lignes = 16 étiquettes par A4. Cartons allongés,
     * étiquettes d'emplacement. Planche centrée : marges (210 − 2 × 99,1 − 2,5) / 2 = 4,65 mm et
     * (297 − 8 × 33,9) / 2 = 12,9 mm. S'appelait à tort « L7169 » (une autre référence Avery,
     * de plus grand format) jusqu'au 24 novembre 2026 ; ce nom reste accepté par {@link #parse}.
     */
    AVERY_L7162("Avery L7162 · 16 étiquettes 99.1 × 33.9 mm",
        2, 8, 99.1, 33.9, 4.65, 12.9, 2.5, 0.0);

    /** Ancien nom de {@link #AVERY_L7162}, encore accepté en entrée de l'API. */
    public static final String LEGACY_L7169 = "AVERY_L7169";

    /** Lecture tolérante d'un nom de gabarit (paramètre d'API) : casse ignorée, ancien nom accepté. */
    public static AveryTemplate parse(String raw) {
        String name = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (name.equals(LEGACY_L7169) || name.equals("L7169")) return AVERY_L7162;
        if (!name.startsWith("AVERY_")) name = "AVERY_" + name;
        return valueOf(name);
    }

    private final String label;
    private final int columns;
    private final int rows;
    private final double widthMm;
    private final double heightMm;
    private final double marginLeftMm;
    private final double marginTopMm;
    private final double horizontalGapMm;
    private final double verticalGapMm;

    AveryTemplate(String label, int columns, int rows,
                  double widthMm, double heightMm,
                  double marginLeftMm, double marginTopMm,
                  double horizontalGapMm, double verticalGapMm) {
        this.label = label;
        this.columns = columns;
        this.rows = rows;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.marginLeftMm = marginLeftMm;
        this.marginTopMm = marginTopMm;
        this.horizontalGapMm = horizontalGapMm;
        this.verticalGapMm = verticalGapMm;
    }

    public String label() { return label; }
    public int columns() { return columns; }
    public int rows() { return rows; }
    public int perPage() { return columns * rows; }
    public double widthMm() { return widthMm; }
    public double heightMm() { return heightMm; }
    public double marginLeftMm() { return marginLeftMm; }
    public double marginTopMm() { return marginTopMm; }
    public double horizontalGapMm() { return horizontalGapMm; }
    public double verticalGapMm() { return verticalGapMm; }
}
