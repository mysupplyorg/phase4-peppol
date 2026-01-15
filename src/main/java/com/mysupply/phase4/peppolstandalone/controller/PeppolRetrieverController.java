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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        return ResponseEntity.ok(retrieveSearchResult.getAsJsonString());
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
                doc.setRetrieved(java.time.Instant.now());
                doc.setRetrievedByInstance(confirmSetting.getInstanceName());
                doc.setRetrievedByConnector(confirmSetting.getConnectorName());
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
