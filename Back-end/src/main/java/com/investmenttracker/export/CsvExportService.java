package com.investmenttracker.export;

import com.investmenttracker.investment.Holding;
import com.investmenttracker.investment.HoldingRepository;
import com.investmenttracker.user.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service responsible for generating CSV content from a user's holdings.
 *
 * Produces a CSV string with columns: Symbol, Shares, Average Cost, Platform.
 * Each row represents one individual holding (not aggregated by symbol).
 * If the user has no holdings, only the header row is returned.
 *
 * Requirements: 7.2, 7.3, 7.4, 7.5
 */
@Service
public class CsvExportService {

    private static final String CSV_HEADER = "Symbol,Shares,Average Cost,Platform";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HoldingRepository holdingRepository;

    public CsvExportService(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    /**
     * Generates CSV content for all holdings belonging to the given user.
     *
     * @param user the authenticated user whose holdings to export
     * @return CSV string with header row and one data row per holding
     */
    public String generateCsv(User user) {
        List<Holding> holdings = holdingRepository.findByUser(user);
        StringBuilder csv = new StringBuilder();
        csv.append(CSV_HEADER).append("\n");

        for (Holding holding : holdings) {
            csv.append(escapeCsvField(holding.getSymbol().getTicker())).append(",");
            csv.append(holding.getQuantity().toPlainString()).append(",");
            csv.append(holding.getAverageCost() != null ? holding.getAverageCost().toPlainString() : "").append(",");
            csv.append(escapeCsvField(holding.getPlatform() != null ? holding.getPlatform() : ""));
            csv.append("\n");
        }

        return csv.toString();
    }

    /**
     * Generates the export filename with the current date.
     *
     * @return filename in format "holdings_export_YYYY-MM-DD.csv"
     */
    public String generateFilename() {
        return "holdings_export_" + LocalDate.now().format(DATE_FORMATTER) + ".csv";
    }

    /**
     * Escapes a CSV field value by wrapping in quotes if it contains
     * commas, quotes, or newlines.
     */
    private String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
