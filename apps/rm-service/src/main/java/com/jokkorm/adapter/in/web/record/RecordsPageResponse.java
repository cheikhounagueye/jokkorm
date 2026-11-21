package com.jokkorm.adapter.in.web.record;

import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import java.util.List;

/**
 * Page de résultats REST. Enveloppe le contenu + métadonnées de pagination, sous
 * un schéma stable (pas de {@code org.springframework.data.domain.Page} renvoyé
 * directement, qui bouge d'une version à l'autre et casse les consommateurs).
 */
public record RecordsPageResponse(
    List<RecordResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {

    public static RecordsPageResponse from(Page<PhysicalArchiveRecord> page) {
        List<RecordResponse> content = page.content().stream()
            .map(RecordResponse::from)
            .toList();
        return new RecordsPageResponse(
            content, page.page(), page.size(), page.totalElements(),
            page.totalPages(), page.isLast());
    }
}
