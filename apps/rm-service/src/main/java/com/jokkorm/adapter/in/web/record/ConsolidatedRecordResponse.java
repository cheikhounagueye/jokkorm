package com.jokkorm.adapter.in.web.record;

import com.jokkorm.application.record.GetConsolidatedRecordUseCase;
import com.jokkorm.application.record.GetConsolidatedRecordUseCase.MasterStatus;
import com.jokkorm.domain.master.MasterArchiveView;

/**
 * Représentation REST de la vue consolidée « 2 gardiens ». La partie {@code local} vient
 * de JokkoRM ; la partie {@code master} vient de la GED/SAE maître via {@link
 * com.jokkorm.domain.master.SaeMasterPort}, ou {@code null} si indisponible/absent.
 *
 * <p>Le frontend affiche {@code local} systématiquement, et {@code master} si présent —
 * sinon bandeau « contenu numérique indisponible ».
 */
public record ConsolidatedRecordResponse(
    RecordResponse local,
    Master master) {

    public static ConsolidatedRecordResponse from(GetConsolidatedRecordUseCase.Result result) {
        return new ConsolidatedRecordResponse(
            RecordResponse.from(result.local()),
            Master.from(result.master(), result.masterStatus()));
    }

    /**
     * Partie maître. {@code status} toujours renseigné ; les autres champs peuvent être null
     * si {@code status != AVAILABLE}.
     */
    public record Master(
        MasterStatus status,
        String title,
        String classificationPath,
        Integer duaYears,
        String finalFate,
        Boolean legalHold,
        String masterPageUrl) {

        public static Master from(MasterArchiveView view, MasterStatus status) {
            if (view == null) {
                return new Master(status, null, null, null, null, null, null);
            }
            return new Master(
                status,
                view.title(),
                view.classificationPath(),
                view.duaYears(),
                view.finalFate(),
                view.legalHold(),
                view.masterPageUrl());
        }
    }
}
