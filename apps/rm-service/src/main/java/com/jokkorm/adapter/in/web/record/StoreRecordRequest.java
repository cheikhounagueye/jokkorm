package com.jokkorm.adapter.in.web.record;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload du POST /records/store?cote=….
 *
 * @param containerBarcode boîte d'arrivée
 * @param transfer         vrai seulement pour changer de boîte un dossier déjà rangé (geste confirmé) ;
 *                         absent ou faux : un dossier déjà dans une autre boîte est refusé (409)
 */
public record StoreRecordRequest(@NotBlank String containerBarcode, Boolean transfer) {

    public boolean isTransfer() {
        return Boolean.TRUE.equals(transfer);
    }
}
