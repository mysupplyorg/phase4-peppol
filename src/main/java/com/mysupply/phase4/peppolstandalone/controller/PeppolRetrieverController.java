/*
 * Copyright (C) 2023-204 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysupply.phase4.peppolstandalone.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helger.base.string.StringHelper;
import com.mysupply.phase4.domain.*;
import com.mysupply.phase4.peppolstandalone.APConfig;
import com.mysupply.phase4.peppolstandalone.dto.*;
import com.mysupply.phase4.persistence.ISBDRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/retriever/v1.0")
public class PeppolRetrieverController {
    static final String HEADER_X_TOKEN = "X-Token";
    private static final Logger LOGGER = LoggerFactory.getLogger(PeppolRetrieverController.class);
    private static final LocalDateTime ONLINE_TIMESTAMP = LocalDateTime.now();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ISBDRepository sbdRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private void setSbdRepository(ISBDRepository sbdRepository) {
        this.sbdRepository = sbdRepository;
    }

    /// Gets a list of documents that have not yet been retrieved.
    @PostMapping(path = "/getNotRetrievedDocumentIds", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNotRetrievedDocumentIds(@RequestHeader(HEADER_X_TOKEN) final String xtoken,
                                                          @RequestBody final String retrieveSearchSettingJSon) {
        ResponseEntity<String> errorResponse = this.validateToken(xtoken);
        if (errorResponse != null)
        {
            return errorResponse;
        }

        RetrieveSearchSetting searchSetting;
        try {
            searchSetting = objectMapper.readValue(retrieveSearchSettingJSon, RetrieveSearchSetting.class);
        } catch (Exception ex) {
            LOGGER.error("Failed to parse RetrieveSearchSetting JSON: ", ex);
            return ResponseEntity
                    .badRequest()
                    .body("Invalid JSON format for RetrieveSearchSetting");
        }

        RetrieveSearchResult retrieveSearchResult = new RetrieveSearchResult();

        // Use the boolean flags to determine if we should retrieve all
        boolean senderWildcard = searchSetting.isRetrieveFromAllSenders();
        boolean receiverWildcard = searchSetting.isRetrieveFromAllReceivers();
        boolean domainWildcard = searchSetting.isRetrieveFromAllDomains();

        // Provide empty lists if null to avoid null pointer exceptions in query
        List<String> senderIds = searchSetting.getSenderIdentifiers() != null
                ? searchSetting.getSenderIdentifiers() : List.of();
        List<String> receiverIds = searchSetting.getReceiverIdentifiers() != null
                ? searchSetting.getReceiverIdentifiers() : List.of();
        List<String> domains = searchSetting.getDomains() != null
                ? searchSetting.getDomains() : List.of();

        // Find all document IDs that match the search criteria and have not yet been retrieved
        List<UUID> documentIds = this.sbdRepository.findNotRetrievedIdsBySearchCriteria(
                senderWildcard, senderIds,
                receiverWildcard, receiverIds,
                domainWildcard, domains
        );

        // Set document IDs directly to the result
        retrieveSearchResult.setDocumentIds(documentIds);
        try {
            String resultJson = objectMapper.writeValueAsString(retrieveSearchResult);
            return ResponseEntity.ok(resultJson);
        } catch (JsonProcessingException e) {
            return ResponseEntity
                    .internalServerError()
                    .body ("Failed to serialize result to JSON: Message "+ e.getMessage());
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body ("Message "+ e.getMessage());
        }
    }


    @PostMapping(path = "/getDocument", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDocument(@RequestHeader(HEADER_X_TOKEN) final String xtoken
                                                            , @RequestBody final String retrieveSettingJSon
    ) {
        ResponseEntity<String> errorResponse = this.validateToken(xtoken);
        if (errorResponse != null)
        {
            return errorResponse;
        }

        RetrieveSetting retrieveSetting;
        try {
            retrieveSetting = objectMapper.readValue(retrieveSettingJSon, RetrieveSetting.class);
        } catch (Exception ex) {
            LOGGER.error("Failed to parse RetrieveSetting JSON: ", ex);
            return ResponseEntity
                    .badRequest()
                    .body("Invalid JSON format for RetrieveSetting");
        }

        Document document = this.sbdRepository.getReferenceById(retrieveSetting.getDocumentId());
        RetrieveData retrieveData = new RetrieveData(document);
        try {
            String retrieveDataJson = objectMapper.writeValueAsString(retrieveData);
            return ResponseEntity.ok(retrieveDataJson);
        } catch (Exception ex) {
            LOGGER.error("Failed to serialize RetrieveData to JSON: ", ex);
            return ResponseEntity
                    .internalServerError()
                    .body("Failed to serialize RetrieveData to JSON");
        }
    }

    @PostMapping(path = "/confirmDocument", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getConfirmDocument(@RequestHeader(HEADER_X_TOKEN) final String xtoken,
                                                          @RequestBody final String confirmSettingJSon) {
        ResponseEntity<String> errorResponse = this.validateToken(xtoken);
        if (errorResponse != null)
        {
            return errorResponse;
        }

        ConfirmSetting confirmSetting;
        try {
            confirmSetting = objectMapper.readValue(confirmSettingJSon, ConfirmSetting.class);
        } catch (Exception ex) {
            LOGGER.error("Failed to parse ConfirmSetting JSON: ", ex);
            return ResponseEntity
                    .badRequest()
                    .body("Invalid JSON format for ConfirmSetting");
        }

        try {
            Optional<Document> document = this.sbdRepository.findById(confirmSetting.getDocumentId());
            if(document.isPresent())
            {
                Document doc = document.get();
                doc.setRetrieved(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
                doc.setRetrievedByInstance(confirmSetting.getInstanceName());
                doc.setRetrievedByConnectorName(confirmSetting.getConnectorName());
                doc.setRetrievedByConnectorId(confirmSetting.getConnectorId());
                doc.setVaxId(confirmSetting.getVaxId());

                this.sbdRepository.save(doc);
                return ResponseEntity.ok("Document with ID " + confirmSetting.getDocumentId() + " has been confirmed as retrieved.");
            }
            else
            {
                return ResponseEntity
                        .badRequest()
                        .body("Document with ID " + confirmSetting.getDocumentId() + " not found.");
            }
        } catch (Exception ex) {
            LOGGER.error("The received data is not valid: ", ex);
            return ResponseEntity
                    .internalServerError()
                    .body("The specified token value does not match the configured required token");
        }
    }

    @GetMapping(path = "/online", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> online() {
        // It is a post method, so it can be used from a browser or monitoring tool to check if the service is online.
        return ResponseEntity
                .ok(ONLINE_TIMESTAMP.format(FORMATTER));
    }

    @GetMapping(path = "/logonCheck", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logonCheck(@RequestHeader(HEADER_X_TOKEN) final String xtoken) {
        ResponseEntity<String> errorResponse = this.validateToken(xtoken);
        if (errorResponse != null)
        {
            return errorResponse;
        }

        return ResponseEntity.ok("OK");
    }

    /// Downloads the document data as XML file.
    @GetMapping(path = "/downloadDocument", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> downloadDocument(@RequestParam("token") final String token,
                                                   @RequestParam("id") final UUID documentId) {
        ResponseEntity<String> errorResponse = this.validateToken(token);
        if (errorResponse != null) {
            return ResponseEntity.badRequest().body(errorResponse.getBody().getBytes());
        }

        try {
            Optional<Document> documentOpt = this.sbdRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Document document = documentOpt.get();
            String filename = "document_" + documentId.toString().substring(0, 8) + ".xml";

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/xml")
                    .body(document.getData());
        } catch (Exception ex) {
            LOGGER.error("Failed to download document: ", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /// Gets all documents in the database for admin overview (without the actual data content).
    @GetMapping(path = "/viewDocuments", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewDocuments(@RequestParam("token") final String token) {
        ResponseEntity<String> errorResponse = this.validateToken(token);
        if (errorResponse != null) {
            return errorResponse;
        }

        try {
            List<DocumentOverview> overviews = this.sbdRepository.findAllDocumentOverviews();
            long totalCount = overviews.size();
            long pendingCount = overviews.stream().filter(d -> d.getRetrieved() == null).count();
            long retrievedCount = overviews.stream().filter(d -> d.getRetrieved() != null).count();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang=\"en\"><head>");
            html.append("<meta charset=\"UTF-8\">");
            html.append("<title>Document Status - phase4 peppol</title>");
            html.append("<style>");
            html.append("* { font-family: sans-serif; box-sizing: border-box; }");
            html.append("body { margin: 20px; background-color: #f5f5f5; }");
            html.append("h1 { color: #333; }");
            html.append(".stats-container { display: flex; gap: 20px; margin-bottom: 20px; flex-wrap: wrap; }");
            html.append(".stat-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); text-align: center; min-width: 150px; }");
            html.append(".stat-card h3 { margin: 0; color: #666; font-size: 14px; }");
            html.append(".stat-card .number { font-size: 36px; font-weight: bold; color: #333; }");
            html.append(".stat-card.pending .number { color: #ff9800; }");
            html.append(".stat-card.retrieved .number { color: #4CAF50; }");
            html.append(".table-container { overflow-x: auto; background: white; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            html.append("table { border-collapse: collapse; }");
            html.append("th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #ddd; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }");
            html.append("th { background-color: #4CAF50; color: white; position: relative; user-select: none; }");
            html.append("th .resizer { position: absolute; right: 0; top: 0; height: 100%; width: 5px; cursor: col-resize; background: rgba(255,255,255,0.3); }");
            html.append("th .resizer:hover { background: rgba(255,255,255,0.6); }");
            html.append("th input { width: 100%; padding: 4px; margin-top: 4px; border: 1px solid #ccc; border-radius: 3px; font-size: 11px; }");
            html.append("tr:hover { background-color: #f5f5f5; }");
            html.append(".status-pending { color: #ff9800; font-weight: bold; }");
            html.append(".status-retrieved { color: #4CAF50; font-weight: bold; }");
            html.append(".refresh-btn { padding: 10px 20px; background-color: #2196F3; color: white; border: none; border-radius: 4px; cursor: pointer; margin-bottom: 20px; text-decoration: none; display: inline-block; }");
            html.append(".refresh-btn:hover { background-color: #1976D2; }");
            html.append(".download-btn { padding: 4px 8px; background-color: #2196F3; color: white; border: none; border-radius: 3px; cursor: pointer; font-size: 11px; text-decoration: none; }");
            html.append(".download-btn:hover { background-color: #1976D2; }");
            html.append(".id-cell { font-family: monospace; font-size: 11px; }");
            html.append(".filter-row input { background: #e8f5e9; }");
            html.append("</style></head><body>");
            html.append("<h1>Document Database Status</h1>");
            html.append("<a class=\"refresh-btn\" href=\"javascript:location.reload()\">🔄 Refresh</a>");

            // Stats cards
            html.append("<div class=\"stats-container\">");
            html.append("<div class=\"stat-card\"><h3>Total Documents</h3><div class=\"number\">").append(totalCount).append("</div></div>");
            html.append("<div class=\"stat-card pending\"><h3>Pending (Not Retrieved)</h3><div class=\"number\">").append(pendingCount).append("</div></div>");
            html.append("<div class=\"stat-card retrieved\"><h3>Retrieved</h3><div class=\"number\">").append(retrievedCount).append("</div></div>");
            html.append("</div>");

            // Documents table with all columns
            html.append("<div class=\"table-container\">");
            html.append("<table id=\"docTable\">");
            html.append("<thead>");
            // Header row
            html.append("<tr>");
            html.append("<th style=\"width:90px;min-width:90px;max-width:90px\">Action<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:90px;min-width:90px;max-width:90px\">Status<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:145px;min-width:145px;max-width:145px\">Created<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:250px;min-width:250px;max-width:250px\">ID<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:150px;min-width:150px;max-width:150px\">Domain<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:200px;min-width:200px;max-width:200px\">Sender<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:200px;min-width:200px;max-width:200px\">Receiver<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:200px;min-width:200px;max-width:200px\">Doc Type<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:200px;min-width:200px;max-width:200px\">Process<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:75px;min-width:75px;max-width:75px\">Protocol<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:300px;min-width:300px;max-width:300px\">Conversation ID<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:300px;min-width:300px;max-width:300px\">Message ID<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:85px;min-width:85px;max-width:85px\">Data Size<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:145px;min-width:145px;max-width:145px\">Retrieved<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:300px;min-width:300px;max-width:300px\">Vax ID<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:170px;min-width:170px;max-width:170px\">Retrieved By Instance<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:200px;min-width:200px;max-width:200px\">Retrieved By Connector ID<div class=\"resizer\"></div></th>");
            html.append("<th style=\"width:180px;min-width:180px;max-width:180px\">Retrieved By Connector<div class=\"resizer\"></div></th>");
            html.append("</tr>");
            // Filter row
            html.append("<tr class=\"filter-row\">");
            html.append("<th></th>"); // No filter for Action
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(1, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(2, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(3, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(4, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(5, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(6, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(7, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(8, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(9, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(10, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(11, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(12, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(13, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(14, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(15, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(16, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("<th><input type=\"text\" onkeyup=\"filterTable(17, this.value)\" placeholder=\"Filter...\"></th>");
            html.append("</tr>");
            html.append("</thead><tbody>");

            if (overviews.isEmpty()) {
                html.append("<tr><td colspan=\"18\">No documents in database</td></tr>");
            } else {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
                for (DocumentOverview doc : overviews) {
                    String status = doc.getRetrieved() != null ? "Retrieved" : "Pending";
                    String statusClass = doc.getRetrieved() != null ? "status-retrieved" : "status-pending";
                    String created = doc.getCreated() != null ? dtf.format(doc.getCreated()) : "-";
                    String retrieved = doc.getRetrieved() != null ? dtf.format(doc.getRetrieved()) : "-";
                    String fullId = doc.getId() != null ? doc.getId().toString() : "-";
                    String vaxId = doc.getVaxId() != null ? doc.getVaxId().toString() : "-";
                    String connectorId = doc.getRetrievedByConnectorId() != null ? doc.getRetrievedByConnectorId().toString() : "-";
                    String downloadUrl = "downloadDocument?token=" + token + "&id=" + fullId;

                    html.append("<tr>");
                    html.append("<td><a class=\"download-btn\" href=\"").append(downloadUrl).append("\" title=\"Download XML\">⬇ Download</a></td>");
                    html.append("<td class=\"").append(statusClass).append("\">").append(status).append("</td>");
                    html.append("<td>").append(created).append("</td>");
                    html.append("<td class=\"id-cell\">").append(fullId).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getDomain())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getSenderIdentifier())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getReceiverIdentifier())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getDocType())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getProcess())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getProtocol())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getConversationId())).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getMessageId())).append("</td>");
                    html.append("<td>").append(formatBytes(doc.getDataSize())).append("</td>");
                    html.append("<td>").append(retrieved).append("</td>");
                    html.append("<td class=\"id-cell\">").append(vaxId).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getRetrievedByInstanceName())).append("</td>");
                    html.append("<td class=\"id-cell\">").append(connectorId).append("</td>");
                    html.append("<td>").append(escapeHtml(doc.getRetrievedByConnectorName())).append("</td>");
                    html.append("</tr>");
                }
            }

            html.append("</tbody></table></div>");

            // JavaScript for filtering and column resizing
            html.append("<script>\n");
            // Filter function
            html.append("function filterTable(colIndex, filterValue) {\n");
            html.append("  var table = document.getElementById('docTable');\n");
            html.append("  var rows = table.getElementsByTagName('tbody')[0].getElementsByTagName('tr');\n");
            html.append("  var filter = filterValue.toLowerCase();\n");
            html.append("  for (var i = 0; i < rows.length; i++) {\n");
            html.append("    var cell = rows[i].getElementsByTagName('td')[colIndex];\n");
            html.append("    if (cell) {\n");
            html.append("      var text = cell.textContent || cell.innerText;\n");
            html.append("      rows[i].style.display = text.toLowerCase().indexOf(filter) > -1 ? '' : 'none';\n");
            html.append("    }\n");
            html.append("  }\n");
            html.append("}\n");
            // Column resize function
            html.append("(function() {\n");
            html.append("  var table = document.getElementById('docTable');\n");
            html.append("  var headerRow = table.querySelector('thead tr:first-child');\n");
            html.append("  var headers = headerRow.querySelectorAll('th');\n");
            html.append("  var resizing = null;\n");
            html.append("  var startX, startWidth, colIndex;\n");
            html.append("  function setColWidth(idx, w) {\n");
            html.append("    var px = w + 'px';\n");
            html.append("    table.querySelectorAll('tr').forEach(function(row) {\n");
            html.append("      var cell = row.children[idx];\n");
            html.append("      if (cell) {\n");
            html.append("        cell.style.width = px;\n");
            html.append("        cell.style.minWidth = px;\n");
            html.append("        cell.style.maxWidth = px;\n");
            html.append("      }\n");
            html.append("    });\n");
            html.append("  }\n");
            html.append("  // Initialize column widths from first header row\n");
            html.append("  headers.forEach(function(th, index) {\n");
            html.append("    var w = th.offsetWidth;\n");
            html.append("    setColWidth(index, w);\n");
            html.append("  });\n");
            html.append("  headers.forEach(function(th, index) {\n");
            html.append("    var resizer = th.querySelector('.resizer');\n");
            html.append("    if (!resizer) return;\n");
            html.append("    resizer.addEventListener('mousedown', function(e) {\n");
            html.append("      resizing = th;\n");
            html.append("      colIndex = index;\n");
            html.append("      startX = e.pageX;\n");
            html.append("      startWidth = th.offsetWidth;\n");
            html.append("      document.body.style.cursor = 'col-resize';\n");
            html.append("      document.body.style.userSelect = 'none';\n");
            html.append("      e.preventDefault();\n");
            html.append("    });\n");
            html.append("  });\n");
            html.append("  document.addEventListener('mousemove', function(e) {\n");
            html.append("    if (!resizing) return;\n");
            html.append("    var newWidth = Math.max(10, startWidth + (e.pageX - startX));\n");
            html.append("    setColWidth(colIndex, newWidth);\n");
            html.append("  });\n");
            html.append("  document.addEventListener('mouseup', function() {\n");
            html.append("    if (resizing) {\n");
            html.append("      resizing = null;\n");
            html.append("      document.body.style.cursor = '';\n");
            html.append("      document.body.style.userSelect = '';\n");
            html.append("    }\n");
            html.append("  });\n");
            html.append("})();\n");
            html.append("</script>");

            html.append("</body></html>");

            return ResponseEntity.ok(html.toString());
        } catch (Exception ex) {
            LOGGER.error("Failed to retrieve documents: ", ex);
            return ResponseEntity
                    .internalServerError()
                    .body("Failed to retrieve documents: " + ex.getMessage());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "-";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        String[] sizes = {"B", "KB", "MB", "GB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        if (i >= sizes.length) i = sizes.length - 1;
        return String.format("%.1f %s", bytes / Math.pow(1024, i), sizes[i]);
    }

    /**
     * Validates the token. Returns null if valid, otherwise returns an error ResponseEntity.
     */
    private ResponseEntity<String> validateToken(final String xtoken)
    {
        if (StringHelper.isEmpty(xtoken))
        {
            return ResponseEntity
                    .badRequest()
                    .body("The specific token header is missing");
        }

        if (!xtoken.equals(APConfig.getPhase4ApiRequiredToken()))
        {
            return ResponseEntity
                    .badRequest()
                    .body("The specified token value does not match the configured required token");
        }

        return null; // Token is valid
    }
}
