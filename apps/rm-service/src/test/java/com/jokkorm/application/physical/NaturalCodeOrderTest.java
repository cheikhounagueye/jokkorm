package com.jokkorm.application.physical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaturalCodeOrderTest {

    @Test
    @DisplayName("Les codes numériques se trient par valeur : 2 avant 10, T02 avant T10")
    void natural_order() {
        List<String> codes = new ArrayList<>(List.of("10", "2", "1", "T10", "T02", "T1", "B", "A"));
        codes.sort(GetRoomViewUseCase::compareNatural);
        assertThat(codes).containsExactly("1", "2", "10", "A", "B", "T1", "T02", "T10");
    }
}
