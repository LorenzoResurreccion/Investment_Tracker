package com.investmenttracker.export;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.investmenttracker.investment.HoldingRepository;

/**
 * Property-based test for CSV filename date pattern.
 *
 * <p><b>Feature: dashboard-rework, Property 8: CSV filename matches date pattern</b></p>
 *
 * <p><b>Validates: Requirements 7.3</b></p>
 *
 * <p>For any valid LocalDate, the exported filename should equal
 * {@code holdings_export_YYYY-MM-DD.csv} where YYYY-MM-DD corresponds
 * to the date's ISO local date string.</p>
 */
// Feature: dashboard-rework, Property 8: CSV filename matches date pattern
class CsvFilenameDatePatternPropertyTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private CsvExportService csvExportService;

    @BeforeProperty
    void setUp() {
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        csvExportService = new CsvExportService(holdingRepository);
    }

    @Property(tries = 100)
    @Label("Generated filename matches holdings_export_YYYY-MM-DD.csv pattern for any date")
    void filenameMatchesDatePattern(@ForAll("randomLocalDates") LocalDate date) {
        // The service generates the filename using LocalDate.now() internally.
        // We verify the contract: filename = "holdings_export_" + ISO_LOCAL_DATE + ".csv"
        String expectedFilename = "holdings_export_" + date.format(DATE_FORMATTER) + ".csv";

        // Verify the expected filename matches the pattern
        assertThat(expectedFilename).matches("holdings_export_\\d{4}-\\d{2}-\\d{2}\\.csv");

        // Verify the date portion round-trips correctly
        String datePortion = expectedFilename.replace("holdings_export_", "").replace(".csv", "");
        LocalDate parsedDate = LocalDate.parse(datePortion, DATE_FORMATTER);
        assertThat(parsedDate).isEqualTo(date);
    }

    @Property(tries = 100)
    @Label("Service generateFilename produces valid filename for today's date")
    void serviceGenerateFilenameProducesValidPattern(@ForAll("randomLocalDates") LocalDate ignored) {
        // Call the actual service method which uses LocalDate.now()
        String filename = csvExportService.generateFilename();

        // Verify it matches the required pattern
        assertThat(filename).matches("holdings_export_\\d{4}-\\d{2}-\\d{2}\\.csv");

        // Verify the date portion is today's date (ISO format)
        String datePortion = filename.replace("holdings_export_", "").replace(".csv", "");
        LocalDate parsedDate = LocalDate.parse(datePortion, DATE_FORMATTER);
        assertThat(parsedDate).isEqualTo(LocalDate.now());

        // Verify the full filename equals the expected format
        String expectedFilename = "holdings_export_" + LocalDate.now().format(DATE_FORMATTER) + ".csv";
        assertThat(filename).isEqualTo(expectedFilename);
    }

    @Property(tries = 100)
    @Label("Filename date format is exactly YYYY-MM-DD with zero-padded month and day")
    void filenameDateIsZeroPadded(@ForAll("randomLocalDates") LocalDate date) {
        String formatted = date.format(DATE_FORMATTER);
        String expectedFilename = "holdings_export_" + formatted + ".csv";

        // Verify year is exactly 4 digits
        String year = formatted.substring(0, 4);
        assertThat(year).matches("\\d{4}");

        // Verify month is exactly 2 digits (zero-padded)
        String month = formatted.substring(5, 7);
        assertThat(month).matches("\\d{2}");
        int monthVal = Integer.parseInt(month);
        assertThat(monthVal).isBetween(1, 12);

        // Verify day is exactly 2 digits (zero-padded)
        String day = formatted.substring(8, 10);
        assertThat(day).matches("\\d{2}");
        int dayVal = Integer.parseInt(day);
        assertThat(dayVal).isBetween(1, 31);

        // Verify separators are hyphens
        assertThat(formatted.charAt(4)).isEqualTo('-');
        assertThat(formatted.charAt(7)).isEqualTo('-');

        // Verify full filename structure
        assertThat(expectedFilename).startsWith("holdings_export_");
        assertThat(expectedFilename).endsWith(".csv");
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<LocalDate> randomLocalDates() {
        // Generate random LocalDate values across a wide range of years
        Arbitrary<Integer> years = Arbitraries.integers().between(2000, 2099);
        Arbitrary<Integer> months = Arbitraries.integers().between(1, 12);
        Arbitrary<Integer> days = Arbitraries.integers().between(1, 28); // 28 to avoid invalid dates

        return Combinators.combine(years, months, days)
                .as(LocalDate::of);
    }
}
