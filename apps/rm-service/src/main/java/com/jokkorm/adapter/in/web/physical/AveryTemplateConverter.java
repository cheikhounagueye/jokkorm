package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.AveryTemplate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Paramètre d'API {@code template} : accepte l'ancien nom {@code AVERY_L7169} (en réalité une
 * planche L7162) et les noms courts ({@code L7160}), pour ne casser aucun appelant existant.
 */
@Component
public class AveryTemplateConverter implements Converter<String, AveryTemplate> {

    @Override
    public AveryTemplate convert(String source) {
        return AveryTemplate.parse(source);
    }
}
