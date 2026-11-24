package com.jokkorm.adapter.in.web.record;

import com.jokkorm.adapter.in.web.physical.PhysicalMovementResponse;
import com.jokkorm.adapter.in.web.physical.RecordMovementRequest;
import com.jokkorm.application.physical.ListRecordMovementsUseCase;
import com.jokkorm.application.physical.RecordMovementUseCase;
import com.jokkorm.application.record.FindRecordByCoteUseCase;
import com.jokkorm.application.record.GetConsolidatedRecordUseCase;
import com.jokkorm.application.record.ListRecordsUseCase;
import com.jokkorm.application.record.StoreRecordInContainerUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture des enveloppes physiques pour le tenant courant. Voir OpenAPI
 * {@code docs/api/openapi.yaml}.
 *
 * <p>Deux endpoints en v1 :
 * <ul>
 *   <li>{@code GET /api/v1/records} — liste paginée, filtre statut optionnel.</li>
 *   <li>{@code GET /api/v1/records/by-cote?cote=X} — détail par cote. Query param
 *       plutôt que path variable : la cote contient souvent {@code /} (ex.
 *       {@code DMG/MP/2024/047}), que Tomcat et Spring Security StrictHttpFirewall
 *       refusent par défaut en path. Query param règle le problème sans hack config.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/records")
@Tag(name = "Records", description = "Enveloppes physiques des archives (lecture).")
public class RecordController {

    private final ListRecordsUseCase listRecords;
    private final FindRecordByCoteUseCase findByCote;
    private final GetConsolidatedRecordUseCase getConsolidated;
    private final StoreRecordInContainerUseCase storeInContainer;
    private final RecordMovementUseCase recordMovement;
    private final ListRecordMovementsUseCase listMovements;

    public RecordController(
        ListRecordsUseCase listRecords,
        FindRecordByCoteUseCase findByCote,
        GetConsolidatedRecordUseCase getConsolidated,
        StoreRecordInContainerUseCase storeInContainer,
        RecordMovementUseCase recordMovement,
        ListRecordMovementsUseCase listMovements) {
        this.listRecords = listRecords;
        this.findByCote = findByCote;
        this.getConsolidated = getConsolidated;
        this.storeInContainer = storeInContainer;
        this.recordMovement = recordMovement;
        this.listMovements = listMovements;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des enveloppes physiques pour le tenant courant.")
    public RecordsPageResponse list(
        @Parameter(description = "Numéro de page (0-based)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Taille de page [1, 200]", example = "20")
        @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Filtre statut optionnel")
        @RequestParam(required = false) PhysicalArchiveStatus status) {
        return RecordsPageResponse.from(
            listRecords.execute(TenantContext.current(), status, PageRequest.of(page, size)));
    }

    @GetMapping("/by-cote")
    @Operation(summary = "Détail d'une enveloppe physique par sa cote (émise par le maître).")
    public RecordResponse findByCote(
        @Parameter(description = "Cote émise par le maître (slashes OK en query param)",
                   example = "DMG/MP/2024/047")
        @RequestParam String cote) {
        return RecordResponse.from(findByCote.execute(TenantContext.current(), Cote.of(cote)));
    }

    @GetMapping("/movements")
    @Operation(
        summary = "Timeline des mouvements physiques pour une cote (phase 5D).",
        description = "Liste chronologique append-only des 6 verbes INGRESS/RELOCATE/LOAN_OUT/"
            + "LOAN_IN/DIGITIZE_REQUEST/EGRESS. Alimentée automatiquement par /records/store "
            + "(INGRESS ou RELOCATE selon qu'il y avait déjà une boîte) et manuellement via "
            + "POST /records/movements.")
    public List<PhysicalMovementResponse> movements(
        @Parameter(description = "Cote du dossier")
        @RequestParam String cote) {
        return listMovements.execute(Cote.of(cote)).stream()
            .map(PhysicalMovementResponse::from)
            .toList();
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enregistre un mouvement physique manuel (prêt, retour, numérisation, destruction).")
    public PhysicalMovementResponse recordMovement(
        @Parameter(description = "Cote du dossier")
        @RequestParam String cote,
        @Valid @RequestBody RecordMovementRequest request) {
        return PhysicalMovementResponse.from(
            recordMovement.execute(Cote.of(cote), request.kind(), request.actor(), request.motive()));
    }

    @PostMapping("/store")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Range un dossier dans une boîte — phase 5C.",
        description = "Lie le record identifié par sa `cote` à la boîte identifiée par son "
            + "`containerBarcode`. La boîte doit déjà avoir été rangée à une POSITION physique. "
            + "Déclenche notifyPhysicalLocation vers le maître avec l'adresse 5 niveaux, "
            + "ajoute un maillon à la chaîne d'audit HMAC. Statut record passe à STORED, "
            + "boîte EMPTY passe à OPEN. Un dossier n'est que dans une seule boîte : déjà dans "
            + "cette boîte → 409 `record-already-in-container` ; dans une autre boîte → 409 "
            + "`record-in-other-container` (boîte actuelle dans `currentBarcode`, `currentLabel`, "
            + "`currentAddress`), sauf `transfer: true` (déplacement RELOCATE tracé) ; transfert "
            + "avec une des deux boîtes en prêt → 409 `record-transfer-blocked`.")
    public RecordResponse store(
        @Parameter(description = "Cote du dossier (slashes OK en query param)")
        @RequestParam String cote,
        @Valid @RequestBody StoreRecordRequest request) {
        return RecordResponse.from(
            storeInContainer.execute(Cote.of(cote), request.containerBarcode(), request.isTransfer()));
    }

    @GetMapping("/consolidated")
    @Operation(
        summary = "Vue consolidée « 2 gardiens » — physique local + champs maître.",
        description = "Agrège le record JokkoRM (status, emplacement, mouvements à venir) "
            + "et les champs détenus par la GED/SAE maître (titre, DUA, sort final, gel "
            + "contentieux) via SaeMasterPort.fetchByCote. En cas d'indisponibilité du maître, "
            + "la section `master.status` passe à UNAVAILABLE ou UNREACHABLE ; le frontend "
            + "affiche alors la vue physique seule avec un bandeau d'information.")
    public ConsolidatedRecordResponse consolidated(
        @Parameter(description = "Cote émise par le maître (slashes OK en query param)",
                   example = "DMG/MP/2024/047")
        @RequestParam String cote) {
        return ConsolidatedRecordResponse.from(
            getConsolidated.execute(TenantContext.current(), Cote.of(cote)));
    }
}
