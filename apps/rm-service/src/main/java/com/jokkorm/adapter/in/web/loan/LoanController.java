package com.jokkorm.adapter.in.web.loan;

import com.jokkorm.application.loan.CancelLoanUseCase;
import com.jokkorm.application.loan.GhostCardPdfGenerator;
import com.jokkorm.application.loan.ListActiveLoansUseCase;
import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.MarkLoanReturnedUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Prêts de boîtes physiques (phase 5E).")
public class LoanController {

    private final RequestLoanUseCase requestUseCase;
    private final MarkLoanOutUseCase outUseCase;
    private final MarkLoanReturnedUseCase returnedUseCase;
    private final CancelLoanUseCase cancelUseCase;
    private final ListActiveLoansUseCase listActive;
    private final GhostCardPdfGenerator ghostPdf;

    public LoanController(
        RequestLoanUseCase requestUseCase,
        MarkLoanOutUseCase outUseCase,
        MarkLoanReturnedUseCase returnedUseCase,
        CancelLoanUseCase cancelUseCase,
        ListActiveLoansUseCase listActive,
        GhostCardPdfGenerator ghostPdf) {
        this.requestUseCase = requestUseCase;
        this.outUseCase = outUseCase;
        this.returnedUseCase = returnedUseCase;
        this.cancelUseCase = cancelUseCase;
        this.listActive = listActive;
        this.ghostPdf = ghostPdf;
    }

    public record RequestLoanRequest(
        @NotNull UUID containerId,
        @NotBlank String borrowerName,
        String borrowerDept,
        String purpose,
        @NotNull @Future Instant expectedReturnAt) {}

    public record CancelRequest(String reason) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une demande de prêt (REQUESTED). Refuse si prêt actif déjà présent.")
    public LoanResponse request(
        @Valid @RequestBody RequestLoanRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "demandeur") String actor) {
        return LoanResponse.from(requestUseCase.execute(
            body.containerId(), body.borrowerName(), body.borrowerDept(), body.purpose(),
            body.expectedReturnAt(), actor));
    }

    @PostMapping("/{id}/out")
    @Operation(summary = "Marque la sortie — téléchargement du fantôme PDF+QR à faire juste après.")
    public LoanResponse markOut(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return LoanResponse.from(outUseCase.execute(id, actor));
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "Marque le retour de la boîte (fantôme à retirer physiquement).")
    public LoanResponse markReturned(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return LoanResponse.from(returnedUseCase.execute(id, actor));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annule une demande REQUESTED avant sortie (motif libre).")
    public LoanResponse cancel(
        @PathVariable UUID id,
        @RequestBody(required = false) CancelRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        String reason = body == null ? null : body.reason();
        return LoanResponse.from(cancelUseCase.execute(id, reason, actor));
    }

    @GetMapping("/active")
    @Operation(summary = "Liste les prêts actuellement OUT ou en retard (triés par date retour).")
    public List<LoanResponse> listActive() {
        return listActive.execute().stream()
            .map(e -> LoanResponse.from(e.loan(), e.overdue()))
            .toList();
    }

    @GetMapping(value = "/{id}/ghost-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharge le fantôme A5 PDF+QR du prêt (à imprimer + glisser à la place de la boîte).")
    public ResponseEntity<byte[]> ghostCardPdf(@PathVariable UUID id) {
        byte[] pdf = ghostPdf.generate(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
            .filename("fantome-" + id + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
