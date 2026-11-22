package com.jokkorm.application.physical;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Génère le fichier modèle Excel que l'archiviste télécharge pour remplir sa
 * topographie puis uploader. Voir {@link ImportTopographyFromExcelUseCase} pour
 * le contrat du format.
 */
@Service
public class TopographyExcelTemplateGenerator {

    public byte[] generate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Topographie");

            // En-tête avec style gras + fond gris clair
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = {"kind", "code", "label", "parent_path"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            // Exemple complet 1 site → 1 magasin → 1 travée → 2 tablettes → 6 positions
            Object[][] example = {
                {"SITE",       "DKR",    "Dakar siège social",       ""},
                {"STOREHOUSE", "MAG-01", "Magasin principal",        "DKR"},
                {"SHELF",      "A",      "Travée A (RH + Finances)", "DKR/MAG-01"},
                {"RACK",       "1",      "Tablette 1 (RH)",          "DKR/MAG-01/A"},
                {"POSITION",   "001",    "Position 001",             "DKR/MAG-01/A/1"},
                {"POSITION",   "002",    "Position 002",             "DKR/MAG-01/A/1"},
                {"POSITION",   "003",    "Position 003",             "DKR/MAG-01/A/1"},
                {"RACK",       "2",      "Tablette 2 (Finances)",    "DKR/MAG-01/A"},
                {"POSITION",   "001",    "Position 001",             "DKR/MAG-01/A/2"},
                {"POSITION",   "002",    "Position 002",             "DKR/MAG-01/A/2"},
                {"POSITION",   "003",    "Position 003",             "DKR/MAG-01/A/2"},
            };
            for (int r = 0; r < example.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < 4; c++) {
                    row.createCell(c).setCellValue(String.valueOf(example[r][c]));
                }
            }

            // Largeurs colonnes
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 40 * 256);
            sheet.setColumnWidth(3, 40 * 256);

            // Feuille d'aide
            Sheet help = workbook.createSheet("Aide");
            String[] helpLines = {
                "FORMAT D'IMPORT TOPOGRAPHIE — JokkoRM phase 5I",
                "",
                "Colonne kind : SITE / STOREHOUSE / SHELF / RACK / POSITION",
                "  SITE       = Site (ex. Dakar siège)",
                "  STOREHOUSE = Magasin dans un site",
                "  SHELF      = Travée dans un magasin",
                "  RACK       = Tablette dans une travée",
                "  POSITION   = Position (feuille, là où les boîtes sont rangées)",
                "",
                "Colonne code : code court identifiant le nœud dans son parent (unique par parent)",
                "  Ex. 'DKR' pour un site, 'MAG-01' pour un magasin, '001' pour une position",
                "",
                "Colonne label : nom lisible optionnel (ex. 'Dakar siège social')",
                "",
                "Colonne parent_path : chaîne des codes des ancêtres séparés par /",
                "  Ex. 'DKR/MAG-01/A/1' = la position doit rejoindre la tablette 1",
                "  Vide pour un SITE.",
                "",
                "Ordre dans le fichier : un enfant NE PEUT PAS être importé avant son parent",
                "  L'import lit ligne par ligne.",
                "",
                "Idempotence : un chemin déjà existant en base est ignoré (safe à rejouer)",
            };
            for (int i = 0; i < helpLines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(helpLines[i]);
            }
            help.setColumnWidth(0, 100 * 256);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot generate Excel template", e);
        }
    }
}
