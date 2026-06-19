package com.investmenttracker.export;

import com.investmenttracker.investment.Holding;
import com.investmenttracker.investment.HoldingRepository;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.user.User;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for CSV generation structure.
 *
 * <p><b>Feature: dashboard-rework, Property 7: CSV generation produces correct structure</b></p>
 *
 * <p><b>Validates: Requirements 7.2</b></p>
 *
 * <p>For any array of holdings with valid symbol, quantity, averageCost, and platform fields,
 * {@code CsvExportService.generateCsv(user)} should produce a CSV string where the first line
 * contains the headers "Symbol,Shares,Average Cost,Platform" and each subsequent line contains
 * the corresponding values for each holding.</p>
 */
// Feature: dashboard-rework, Property 7: CSV generation produces correct structure
class CsvGenerationStructurePropertyTest {

    private HoldingRepository holdingRepository;
    private CsvExportService csvExportService;
    private User testUser;
    private List<Holding> currentHoldings;
    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        currentHoldings = new ArrayList<>();
        idGenerator = new AtomicLong(1);

        holdingRepository = mock(HoldingRepository.class);
        when(holdingRepository.findByUser(any(User.class))).thenAnswer(invocation ->
                new ArrayList<>(currentHoldings)
        );

        csvExportService = new CsvExportService(holdingRepository);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setCognitoSub("sub-test-123");
    }

    @Property(tries = 100)
    @Label("CSV first line is exact header and subsequent lines count equals number of holdings")
    void csvHasCorrectHeaderAndLineCount(
            @ForAll("holdingsList") List<HoldingData> holdingsData
    ) {
        // Set up holdings in the mock
        currentHoldings.clear();
        for (HoldingData data : holdingsData) {
            currentHoldings.add(createHolding(data));
        }

        // Generate CSV
        String csv = csvExportService.generateCsv(testUser);

        // Parse full CSV into logical records (handles multi-line quoted fields)
        List<List<String>> records = parseCsv(csv);

        // First record must be exact header
        assertThat(records).isNotEmpty();
        assertThat(String.join(",", records.get(0)))
                .isEqualTo("Symbol,Shares,Average Cost,Platform");

        // Data records count equals number of holdings
        assertThat(records.size() - 1).isEqualTo(holdingsData.size());
    }

    @Property(tries = 100)
    @Label("Each CSV data line contains corresponding holding values")
    void csvDataLinesMatchHoldings(
            @ForAll("holdingsList") List<HoldingData> holdingsData
    ) {
        // Set up holdings in the mock
        currentHoldings.clear();
        for (HoldingData data : holdingsData) {
            currentHoldings.add(createHolding(data));
        }

        // Generate CSV
        String csv = csvExportService.generateCsv(testUser);

        // Parse full CSV into logical records
        List<List<String>> records = parseCsv(csv);

        // For each holding, check the corresponding data record
        for (int i = 0; i < holdingsData.size(); i++) {
            HoldingData data = holdingsData.get(i);
            List<String> fields = records.get(i + 1); // skip header

            assertThat(fields).hasSize(4);

            // Symbol field
            assertThat(fields.get(0)).isEqualTo(data.ticker());

            // Shares field
            assertThat(fields.get(1)).isEqualTo(data.quantity().toPlainString());

            // Average Cost field
            String expectedAvgCost = data.averageCost() != null
                    ? data.averageCost().toPlainString()
                    : "";
            assertThat(fields.get(2)).isEqualTo(expectedAvgCost);

            // Platform field
            String expectedPlatform = data.platform() != null ? data.platform() : "";
            assertThat(fields.get(3)).isEqualTo(expectedPlatform);
        }
    }

    @Property(tries = 100)
    @Label("CSV fields containing special characters are properly escaped")
    void csvSpecialCharactersAreEscaped(
            @ForAll("holdingsWithSpecialChars") List<HoldingData> holdingsData
    ) {
        // Set up holdings in the mock
        currentHoldings.clear();
        for (HoldingData data : holdingsData) {
            currentHoldings.add(createHolding(data));
        }

        // Generate CSV
        String csv = csvExportService.generateCsv(testUser);

        // Parse full CSV into logical records (handles multi-line quoted fields)
        List<List<String>> records = parseCsv(csv);

        // Verify each data record can be parsed back to original values
        assertThat(records.size() - 1).isEqualTo(holdingsData.size());
        for (int i = 0; i < holdingsData.size(); i++) {
            HoldingData data = holdingsData.get(i);
            List<String> fields = records.get(i + 1); // skip header

            assertThat(fields).hasSize(4);

            // Values should match originals after un-escaping
            assertThat(fields.get(0)).isEqualTo(data.ticker());
            assertThat(fields.get(1)).isEqualTo(data.quantity().toPlainString());
            String expectedPlatform = data.platform() != null ? data.platform() : "";
            assertThat(fields.get(3)).isEqualTo(expectedPlatform);
        }
    }

    // --- Data records ---

    record HoldingData(
            String ticker,
            BigDecimal quantity,
            BigDecimal averageCost,
            String platform
    ) {}

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<HoldingData>> holdingsList() {
        return holdingData().list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<HoldingData>> holdingsWithSpecialChars() {
        return holdingDataWithSpecialChars().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<HoldingData> holdingData() {
        Arbitrary<String> ticker = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                .ofMinLength(1)
                .ofMaxLength(10);

        Arbitrary<BigDecimal> quantity = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);

        Arbitrary<BigDecimal> averageCost = Arbitraries.oneOf(
                Arbitraries.just((BigDecimal) null),
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("0.01"), new BigDecimal("999999.99"))
                        .ofScale(8)
        );

        Arbitrary<String> platform = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withChars(' ', '-', '_')
                        .ofMinLength(1)
                        .ofMaxLength(50)
        );

        return Combinators.combine(ticker, quantity, averageCost, platform)
                .as(HoldingData::new);
    }

    private Arbitrary<HoldingData> holdingDataWithSpecialChars() {
        // Tickers that may contain commas or quotes (testing CSV escaping)
        Arbitrary<String> ticker = Arbitraries.oneOf(
                Arbitraries.of("AAPL", "GOOG", "MSFT"),
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withChars(',', '"', '\n')
                        .ofMinLength(1)
                        .ofMaxLength(10)
        );

        Arbitrary<BigDecimal> quantity = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("9999.99"))
                .ofScale(4);

        Arbitrary<BigDecimal> averageCost = Arbitraries.oneOf(
                Arbitraries.just((BigDecimal) null),
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("1.00"), new BigDecimal("500.00"))
                        .ofScale(2)
        );

        // Platforms with special CSV characters
        Arbitrary<String> platform = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.of("Robin,hood", "Say \"hi\"", "Line\nBreak", "Normal"),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars(',', '"', '\n')
                        .ofMinLength(1)
                        .ofMaxLength(20)
        );

        return Combinators.combine(ticker, quantity, averageCost, platform)
                .as(HoldingData::new);
    }

    // --- Helper methods ---

    private Holding createHolding(HoldingData data) {
        Symbol symbol = new Symbol();
        symbol.setId(idGenerator.getAndIncrement());
        symbol.setTicker(data.ticker());

        Holding holding = new Holding();
        holding.setId(idGenerator.getAndIncrement());
        holding.setUser(testUser);
        holding.setSymbol(symbol);
        holding.setQuantity(data.quantity());
        holding.setAverageCost(data.averageCost());
        holding.setPlatform(data.platform());

        return holding;
    }

    /**
     * Parses a full CSV string into logical records, correctly handling
     * multi-line quoted fields. Each record is a list of unescaped field values.
     * Doubled quotes ("") are unescaped back to single quotes.
     */
    private List<List<String>> parseCsv(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;

        while (i < csv.length()) {
            char c = csv.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Check if this is an escaped quote (doubled)
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i += 2;
                    } else {
                        // End of quoted field
                        inQuotes = false;
                        i++;
                    }
                } else {
                    currentField.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    currentRecord.add(currentField.toString());
                    currentField = new StringBuilder();
                    i++;
                } else if (c == '\n') {
                    // End of record
                    currentRecord.add(currentField.toString());
                    currentField = new StringBuilder();
                    records.add(currentRecord);
                    currentRecord = new ArrayList<>();
                    i++;
                } else {
                    currentField.append(c);
                    i++;
                }
            }
        }

        // Handle last record if it doesn't end with newline
        if (!currentField.isEmpty() || !currentRecord.isEmpty()) {
            currentRecord.add(currentField.toString());
            records.add(currentRecord);
        }

        return records;
    }
}
