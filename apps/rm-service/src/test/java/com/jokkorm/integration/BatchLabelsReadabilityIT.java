package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.jokkorm.application.physical.AveryTemplate;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.application.physical.BatchLabelsPdfGenerator;
import com.jokkorm.application.physical.CreateContainerBatchUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Planches d'étiquettes d'un lot : chaque étiquette de la première page, découpée selon le
 * gabarit Avery et rendue à 300 dpi (une imprimante de bureau), se relit et donne le bon code.
 * Avant, le libellé passait sur les barres et une barre faisait un pixel étiré.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class BatchLabelsReadabilityIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jokkorm_it")
            .withUsername("jokkorm")
            .withPassword("jokkorm")
            .withReuse(true);

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration");
        registry.add("jokkorm.security.enabled", () -> "false");
    }

    private static final int DPI = 300;

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateContainerBatchUseCase createBatch;
    @Autowired BatchLabelsPdfGenerator labels;

    private TenantId tenant;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("lbl-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AveryTemplate.class)
    @DisplayName("Code128 : chaque étiquette de la page se relit, libellé accentué imprimé à part")
    void everyCode128LabelReads(AveryTemplate template) throws Exception {
        assertEveryLabelReads(template, Symbology.CODE128);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AveryTemplate.class)
    @DisplayName("QR : chaque étiquette de la page se relit")
    void everyQrLabelReads(AveryTemplate template) throws Exception {
        assertEveryLabelReads(template, Symbology.QR);
    }

    private void assertEveryLabelReads(AveryTemplate template, Symbology symbology) throws Exception {
        CreateContainerBatchUseCase.Result batch = as(() -> createBatch.execute(
            "LOT-" + template.name(), template.perPage(),
            "Marchés publics DMG " + symbology.name() + " " + template.name(), 50, "it"));
        byte[] pdf = as(() -> labels.generate(batch.batch().id(), template, symbology, "it"));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Marchés publics DMG");
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, DPI);
            List<String> expected = batch.containers().stream().map(PhysicalContainer::barcode).toList();
            List<String> read = new ArrayList<>();
            for (int slot = 0; slot < template.perPage(); slot++) {
                read.add(decode(cell(page, template, slot)));
            }
            assertThat(read).as("%s %s", template, symbology).containsExactlyElementsOf(expected);
        }
    }

    private static BufferedImage cell(BufferedImage page, AveryTemplate t, int slot) {
        double px = DPI / 25.4;
        int col = slot % t.columns();
        int row = slot / t.columns();
        int x = (int) Math.round((t.marginLeftMm() + col * (t.widthMm() + t.horizontalGapMm())) * px);
        int y = (int) Math.round((t.marginTopMm() + row * (t.heightMm() + t.verticalGapMm())) * px);
        int w = (int) Math.round(t.widthMm() * px);
        int h = (int) Math.round(t.heightMm() * px);
        return page.getSubimage(x, y, Math.min(w, page.getWidth() - x), Math.min(h, page.getHeight() - y));
    }

    private static String decode(BufferedImage image) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))), hints).getText();
        } catch (NotFoundException e) {
            return "(illisible)";
        }
    }
}
