package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.jokkorm.application.loan.GhostCardPdfGenerator;
import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.MagasinScanService;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fantôme de prêt avec son propre code (étude du 24 novembre 2026, D10) : le scan le reconnaît
 * comme fantôme, jamais comme la boîte, et le PDF imprimé ne porte plus le code-barres de la boîte.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class GhostCodeIT {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired RequestLoanUseCase requestLoan;
    @Autowired MarkLoanOutUseCase markOut;
    @Autowired MagasinScanService scan;
    @Autowired GhostCardPdfGenerator ghostPdf;

    private TenantId tenant;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("ghost-" + UUID.randomUUID().toString().substring(0, 8));
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

    private PhysicalLoan loanOut(PhysicalContainer box) {
        PhysicalLoan loan = as(() -> requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));
        as(() -> markOut.execute(loan.id(), "it"));
        return loan;
    }

    @Test
    @DisplayName("Chaque prêt a son code PRT- ; scanné, il est reconnu comme fantôme, avec la boîte en clair")
    void scanResolvesGhost() {
        PhysicalContainer box = as(() -> createBox.execute("Boîte prêtée fantôme", null, null));
        PhysicalLoan loan = loanOut(box);

        assertThat(loan.ghostCode()).matches("^PRT-[0-9A-Z]{8}$");
        var r = as(() -> scan.resolve(loan.ghostCode().toLowerCase()));
        assertThat(r.container()).isNull();
        assertThat(r.position()).isNull();
        assertThat(r.ghost().loan().id()).isEqualTo(loan.id());
        assertThat(r.ghost().box().container().barcode()).isEqualTo(box.barcode());
        assertThat(r.ghost().box().onLoan()).isTrue();

        // La boîte elle-même reste une boîte.
        assertThat(as(() -> scan.resolve(box.barcode())).container()).isNotNull();
    }

    @Test
    @DisplayName("Le fantôme imprimé : ses codes-barres portent le code du prêt, jamais celui de la boîte")
    void pdfCarriesGhostCodeOnly() throws Exception {
        PhysicalContainer box = as(() -> createBox.execute("Boîte du fantôme imprimé", null, null));
        PhysicalLoan loan = loanOut(box);

        byte[] pdf = as(() -> ghostPdf.generate(loan.id()));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Fantome " + loan.ghostCode()).contains(box.barcode());

            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 200);
            List<String> decoded = decodeAll(page);
            assertThat(decoded).isNotEmpty().allMatch(loan.ghostCode()::equals);
            assertThat(decoded).doesNotContain(box.barcode());
        }
    }

    @Test
    @DisplayName("Deux prêts d'un même espace ne partagent jamais un code de fantôme")
    void ghostCodeUniquePerTenant() {
        PhysicalContainer a = as(() -> createBox.execute("Boîte fantôme A", null, null));
        PhysicalContainer b = as(() -> createBox.execute("Boîte fantôme B", null, null));
        PhysicalLoan first = loanOut(a);
        PhysicalLoan second = loanOut(b);
        assertThat(first.ghostCode()).isNotEqualTo(second.ghostCode());

        assertThatThrownBy(() -> jdbc.update("UPDATE physical_loan SET ghost_code = ? WHERE id = ?",
                first.ghostCode(), second.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static List<String> decodeAll(BufferedImage image) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        try {
            Result[] results = new GenericMultipleBarcodeReader(new MultiFormatReader()).decodeMultiple(bitmap, hints);
            return Arrays.stream(results).map(Result::getText).toList();
        } catch (NotFoundException e) {
            return List.of();
        }
    }
}
