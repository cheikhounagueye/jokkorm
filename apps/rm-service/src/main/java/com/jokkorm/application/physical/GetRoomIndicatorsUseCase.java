package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.BoxFormatRepository;
import com.jokkorm.domain.physical.RoomLimits;
import com.jokkorm.domain.physical.RoomSpec;
import com.jokkorm.domain.physical.RoomSpecRepository;
import com.jokkorm.domain.physical.StorageLocation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicateurs d'une salle en mètres linéaires (pattern 10 § 3, 5T.D) : installés, occupés,
 * libres, par épi ; boîtes qui tiennent encore par format ; densité ; charge au sol estimée ;
 * date de saturation. Rien n'est inventé : un indicateur dont la donnée manque n'est pas
 * calculé et dit pourquoi.
 */
@Service
public class GetRoomIndicatorsUseCase {

    /** Fenêtre d'observation du rythme d'entrée. */
    public static final int WINDOW_DAYS = 365;
    /** En dessous, le rythme d'entrée n'a pas de sens (ex. une salle remplie d'un coup). */
    public static final int MIN_HISTORY_DAYS = 90;
    private static final double DAYS_PER_MONTH = 30.44;

    public record Line(String key, String label, int travees, int boxes, long installedMm, long usedMm,
                       long placeableMm, double rate) {}

    public record FitCount(String code, String label, int spineMm, long boxes) {}

    /**
     * Charge au sol estimée, indicative : ml occupés × poids moyen au ml ÷ emprise du mobilier.
     * À faire confirmer par un bureau d'études structure.
     */
    public record Load(double kgPerMl, double estimatedKg, double footprintM2, Double kgPerM2,
                       Integer limitKgM2, boolean overLimit) {}

    /**
     * @param historyDays ancienneté de la plus ancienne boîte de la salle (jours)
     * @param intakeMm    dos des boîtes arrivées dans la fenêtre
     * @param mmPerMonth  rythme d'entrée moyen (null si non calculable)
     * @param monthsLeft  mois avant saturation (null si non calculable)
     * @param date        date de saturation estimée (null si non calculable)
     * @param note        pourquoi ce n'est pas calculé, le cas échéant
     */
    public record Saturation(int windowDays, long historyDays, long intakeMm, Double mmPerMonth,
                             Double monthsLeft, LocalDate date, String note) {}

    public record Indicators(long installedMm, long usedMm, long freeMm, long placeableMm, double rate,
                             int boxes, List<Line> byAisle, List<FitCount> capacityByFormat,
                             Double areaM2, Double densityMlPerM2, Load load, String loadNote,
                             Saturation saturation) {}

    private final GetRoomViewUseCase roomView;
    private final RoomSpecRepository specs;
    private final BoxFormatRepository formats;
    private final ShelfSpaceService space;
    private final Clock clock;

    public GetRoomIndicatorsUseCase(GetRoomViewUseCase roomView, RoomSpecRepository specs,
                                    BoxFormatRepository formats, ShelfSpaceService space, Clock clock) {
        this.roomView = roomView;
        this.specs = specs;
        this.formats = formats;
        this.space = space;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Indicators execute(UUID storehouseId) {
        TenantId tenant = TenantContext.current();
        RoomView view = roomView.execute(storehouseId);
        RoomSpec spec = specs.findByStorehouse(tenant, storehouseId).orElse(null);
        RoomView.Occupancy o = view.occupancy();

        // Par épi (les travées sans épi forment une ligne à part).
        Map<String, List<RoomView.Shelf>> byAisle = new LinkedHashMap<>();
        view.shelves().stream()
            .sorted(Comparator.comparing((RoomView.Shelf s) -> aisleSortKey(s.shelf().spec().aisleCode())))
            .forEach(s -> byAisle.computeIfAbsent(
                s.shelf().spec().aisleCode() == null ? "" : s.shelf().spec().aisleCode(), k -> new ArrayList<>()).add(s));
        List<Line> lines = new ArrayList<>();
        byAisle.forEach((code, shelves) -> {
            RoomView.Occupancy sum = shelves.stream().map(RoomView.Shelf::occupancy)
                .reduce(RoomView.Occupancy.EMPTY, RoomView.Occupancy::plus);
            lines.add(new Line(code, code.isEmpty() ? "Sans épi" : "Épi " + code, shelves.size(), sum.boxes(),
                sum.lengthMm(), sum.usedMm(), sum.placeableMm(), round3(sum.rate())));
        });

        // Ce qui tient encore, tablette par tablette (une boîte ne se coupe pas entre deux tablettes).
        List<RoomView.Rack> racks = view.shelves().stream().flatMap(s -> s.racks().stream()).toList();
        List<FitCount> capacity = formats.findAll(tenant).stream()
            .filter(BoxFormat::active)
            .sorted(Comparator.comparingInt(BoxFormat::spineMm))
            .map(f -> new FitCount(f.code(), f.label(), f.spineMm(),
                racks.stream().mapToLong(r -> Math.max(0, r.occupancy().placeableMm()) / f.spineMm()).sum()))
            .toList();

        Double area = spec == null ? null : spec.effectiveAreaM2();
        Double density = area == null || area <= 0 ? null : round2(o.lengthMm() / 1000.0 / area);

        List<StorageLocation> shelfNodes = view.shelves().stream().map(RoomView.Shelf::shelf).toList();
        RoomLimits.Report limits = RoomLimits.check(spec, shelfNodes,
            s -> s.lengthMm().orElse(space.defaultTabletteMm()));
        Load load = null;
        String loadNote;
        if (spec == null || spec.avgWeightKgPerMl() == null) {
            loadNote = "Poids moyen au mètre linéaire non renseigné : charge non calculée.";
        } else {
            double kg = o.usedMm() / 1000.0 * spec.avgWeightKgPerMl();
            Double perM2 = limits.footprintM2() > 0 ? round1(kg / limits.footprintM2()) : null;
            boolean over = perM2 != null && spec.floorLoadKgM2() != null && perM2 > spec.floorLoadKgM2();
            load = new Load(spec.avgWeightKgPerMl(), round1(kg), round2(limits.footprintM2()), perM2,
                spec.floorLoadKgM2(), over);
            loadNote = perM2 == null ? "Profondeur des travées non mesurée : charge au m² non calculée." : null;
        }

        return new Indicators(o.lengthMm(), o.usedMm(), Math.max(0, o.lengthMm() - o.usedMm()), o.placeableMm(),
            round3(o.rate()), o.boxes(), lines, capacity, area == null ? null : round2(area), density,
            load, loadNote, saturation(view, o.placeableMm()));
    }

    /**
     * Rythme d'entrée = dos des boîtes arrivées à leur place actuelle sur les 12 derniers mois.
     * Date d'arrivée : dernier rangement ou déplacement de la boîte (à défaut sa création) ;
     * un déplacement interne à la salle compte donc comme une entrée (léger excès, prudent).
     */
    private Saturation saturation(RoomView view, long placeableMm) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        List<RoomView.Box> boxes = view.shelves().stream().flatMap(s -> s.racks().stream())
            .flatMap(r -> r.positions().stream()).flatMap(p -> p.boxes().stream()).toList();
        if (boxes.isEmpty()) {
            return new Saturation(WINDOW_DAYS, 0, 0, null, null, null, "Salle vide : aucun rythme d'entrée observé.");
        }
        Instant windowStart = clock.instant().minus(WINDOW_DAYS, ChronoUnit.DAYS);
        LocalDate oldest = today;
        long intake = 0;
        for (RoomView.Box b : boxes) {
            Instant arrived = b.container().lastMovedAt().orElse(b.container().createdAt());
            LocalDate d = LocalDate.ofInstant(arrived, zone);
            if (d.isBefore(oldest)) oldest = d;
            if (!arrived.isBefore(windowStart)) intake += b.spineMm();
        }
        long history = ChronoUnit.DAYS.between(oldest, today);
        if (history < MIN_HISTORY_DAYS) {
            return new Saturation(WINDOW_DAYS, history, intake, null, null, null,
                "Historique insuffisant : %d jour%s (%d jours au moins)".formatted(history, history > 1 ? "s" : "", MIN_HISTORY_DAYS));
        }
        double months = Math.min(WINDOW_DAYS, history) / DAYS_PER_MONTH;
        double perMonth = intake / months;
        if (perMonth <= 0) {
            return new Saturation(WINDOW_DAYS, history, 0, 0.0, null, null,
                "Aucune entrée depuis 12 mois : pas de saturation prévisible.");
        }
        double left = placeableMm / perMonth;
        LocalDate date = left > 1200 ? null : today.plusDays(Math.round(left * DAYS_PER_MONTH));
        return new Saturation(WINDOW_DAYS, history, intake, round1(perMonth), round1(left), date,
            left > 1200 ? "Plus de 100 ans au rythme actuel." : null);
    }

    /** E2 avant E10 ; sans épi en dernier. */
    private static String aisleSortKey(String code) {
        if (code == null) return "~";
        String digits = code.replaceAll("\\D", "");
        return digits.isEmpty() ? code : code.replaceAll("\\d", "") + "%06d".formatted(Long.parseLong(digits.length() > 9 ? digits.substring(0, 9) : digits));
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
    private static double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
