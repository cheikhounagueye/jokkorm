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

    /** L7169 : 99.1 × 33.9 mm, 2 colonnes × 8 lignes = 16 étiquettes par A4. Cartons allongés. */
    AVERY_L7169("Avery L7169 · 16 étiquettes 99.1 × 33.9 mm",
        2, 8, 99.1, 33.9, 4.0, 13.0, 2.5, 0.0);

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
