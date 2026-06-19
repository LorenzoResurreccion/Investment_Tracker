package com.investmenttracker.export;

import com.investmenttracker.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for exporting user holdings as a CSV file.
 *
 * Mapped to GET /api/investments/export. Accepts authentication via
 * standard Authorization Bearer header or via a "token" query parameter
 * (to support browser window.open downloads).
 *
 * Requirements: 7.2, 7.3, 7.4, 7.5
 */
@RestController
@RequestMapping("/api/investments")
public class CsvExportController {

    private final CsvExportService csvExportService;

    public CsvExportController(CsvExportService csvExportService) {
        this.csvExportService = csvExportService;
    }

    /**
     * Exports the authenticated user's holdings as a CSV file.
     *
     * Returns Content-Type: text/csv with a Content-Disposition header
     * triggering a browser download with filename "holdings_export_YYYY-MM-DD.csv".
     *
     * @param request the HTTP request (used to extract authenticated user)
     * @return CSV file as response body with appropriate headers
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportHoldings(HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        String csvContent = csvExportService.generateCsv(user);
        String filename = csvExportService.generateFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(csvContent);
    }
}
