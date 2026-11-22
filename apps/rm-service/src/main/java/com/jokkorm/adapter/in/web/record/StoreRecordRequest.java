package com.jokkorm.adapter.in.web.record;

import jakarta.validation.constraints.NotBlank;

/** Payload du POST /records/by-cote/{cote}/store. */
public record StoreRecordRequest(@NotBlank String containerBarcode) {}
