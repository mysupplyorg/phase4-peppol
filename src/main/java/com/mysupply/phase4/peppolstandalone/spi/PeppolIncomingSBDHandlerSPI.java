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
package com.mysupply.phase4.peppolstandalone.spi;



import com.helger.annotation.style.IsSPIImplementation;

import com.helger.http.header.HttpHeaderMap;
import com.helger.security.certificate.CertificateHelper;
import com.mysupply.phase4.ICountryCodeMapper;
import com.mysupply.phase4.domain.Document;
import com.mysupply.phase4.peppolstandalone.APConfig;
import com.mysupply.phase4.peppolstandalone.context.SpringContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.stereotype.Component;
import org.unece.cefact.namespaces.sbdh.StandardBusinessDocument;

import com.helger.collection.commons.ICommonsList;
import com.helger.peppol.reporting.api.PeppolReportingItem;
import com.helger.peppol.reporting.api.backend.PeppolReportingBackend;
import com.helger.peppol.reporting.api.backend.PeppolReportingBackendException;
import com.helger.peppol.sbdh.PeppolSBDHData;
import com.helger.phase4.config.AS4Configuration;
import com.helger.phase4.ebms3header.Ebms3Error;
import com.helger.phase4.ebms3header.Ebms3UserMessage;
import com.helger.phase4.incoming.IAS4IncomingMessageMetadata;
import com.helger.phase4.incoming.IAS4IncomingMessageState;
import com.helger.phase4.peppol.servlet.IPhase4PeppolIncomingSBDHandlerSPI;
import com.helger.phase4.peppol.servlet.Phase4PeppolServletMessageProcessorSPI;
import com.mysupply.phase4.persistence.ISBDRepository;


import jakarta.annotation.Nonnull;

/**
 * This is a way of handling incoming Peppol messages
 *
 * @author Philip Helger
 */
@IsSPIImplementation
@Component
@Configurable
public class PeppolIncomingSBDHandlerSPI implements IPhase4PeppolIncomingSBDHandlerSPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeppolIncomingSBDHandlerSPI.class);

    @Autowired
    private ISBDRepository sbdRepository;

    @Autowired
    private ICountryCodeMapper countryCodeMapper;

    public PeppolIncomingSBDHandlerSPI() {
        SpringContextHolder.autowireBean(this);
    }

    public void handleIncomingSBD(@Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                  @Nonnull final HttpHeaderMap aHeaders,
                                  @Nonnull final Ebms3UserMessage aUserMessage,
                                  @Nonnull final byte[] aSBDBytes,
                                  @Nonnull final StandardBusinessDocument aSBD,
                                  @Nonnull final PeppolSBDHData aPeppolSBD,
                                  @Nonnull final IAS4IncomingMessageState aIncomingState,
                                  @Nonnull final ICommonsList<Ebms3Error> aProcessingErrorMessages) throws Exception {

        final String sMyPeppolSeatID = APConfig.getMyPeppolSeatID ();
        try {
            // Example code snippets how to get data
            String c1 = aPeppolSBD.getSenderAsIdentifier().getURIEncoded();
            String c2 = CertificateHelper.getPEMEncodedCertificate (aIncomingState.getSigningCertificate ());
            String c4 = aPeppolSBD.getReceiverAsIdentifier().getURIEncoded();
            String docType = aPeppolSBD.getDocumentTypeAsIdentifier().getURIEncoded();
            String process = aPeppolSBD.getProcessAsIdentifier().getURIEncoded();
            String countryC1 = aPeppolSBD.getCountryC1();

            StringBuilder sb = new StringBuilder();
            sb.append("Received a new Peppol Message").append(System.lineSeparator())
                .append("  C1 = ").append(c1).append(System.lineSeparator())
                .append("  C2 = ").append(c2).append(System.lineSeparator())
                .append("  C3 = ").append(sMyPeppolSeatID).append(System.lineSeparator())
                .append("  C4 = ").append(c4).append(System.lineSeparator())
                .append("  DocType = ").append(docType).append(System.lineSeparator())
                .append("  Process = ").append(process).append(System.lineSeparator())
                .append("  CountryC1 = ").append(c1).append(System.lineSeparator())
                .append("  CountryC2 = ").append(countryC1).append(System.lineSeparator())
                .append("  CountryC4 = ").append(c4);
            LOGGER.info(sb.toString());
        } catch (NullPointerException ex) {
            LOGGER.error("An error occurred.", ex);
        }

        try {
            String c1 = aPeppolSBD.getSenderAsIdentifier().getURIEncoded();
            String c4 = aPeppolSBD.getReceiverAsIdentifier().getURIEncoded();
            String docType = aPeppolSBD.getDocumentTypeAsIdentifier().getURIEncoded();
            String process = aPeppolSBD.getProcessAsIdentifier().getURIEncoded();
            String domain = this.GetDomain(aHeaders);
            String messageID = aUserMessage.getMessageInfo().getMessageId();
            String conversationID = null;// aUserMessage.getMessageInfo().getConversationId();
            String senderCertificate = CertificateHelper.getPEMEncodedCertificate(aIncomingState.getSigningCertificate());
            String receiverCertificate = CertificateHelper.getPEMEncodedCertificate(aIncomingState.getEncryptionCertificate());


            Document documentToStore = Document.builder()
                .data(aSBDBytes)
                .domain(domain)
                .senderIdentifier(c1)
                .receiverIdentifier(c4)
                .docType(docType)
                .process(process)
                    .
                .build();

            this.sbdRepository.save(documentToStore);
            LOGGER.info("SBD saved successfully");
        } catch (Exception ex) {
            LOGGER.error("Failed to save SBD", ex);
            throw new Exception("Failed to save SBD");
        }

        new Thread(() -> {
            final boolean createPeppolReportingItem = AS4Configuration
                    .getConfig()
                    .getAsBoolean("peppol.createReportingItem");

            if (createPeppolReportingItem)
                try {
                    LOGGER.info("Creating Peppol Reporting Item and storing it");

                    // TODO determine correct values
                    final String sC3ID = aPeppolSBD.getReceiverAsIdentifier().getURIEncoded();
                    final String sC4CountryCode = this.countryCodeMapper.mapCountryCode(aPeppolSBD.getReceiverScheme(), aPeppolSBD.getReceiverValue()) ;// "DK"; // incorrect, we need to determine the country code like in VAX
                    final String sEndUserID = "EndUserID";
                    final PeppolReportingItem aReportingItem = Phase4PeppolServletMessageProcessorSPI.createPeppolReportingItemForReceivedMessage(aUserMessage,
                            aPeppolSBD,
                            aIncomingState,
                            sC3ID,
                            sC4CountryCode,
                            sEndUserID);

                    PeppolReportingBackend.withBackendDo(APConfig.getConfig(),
                            aBackend -> aBackend.storeReportingItem(aReportingItem));
                } catch (final PeppolReportingBackendException ex) {
                    LOGGER.error("Failed to store Peppol Reporting Item", ex);
                    // TODO improve error handling
                }
        }).start();
    }

    private String GetDomain(HttpHeaderMap aHeaders)
    {
        return "?";
    }

    @Autowired
    private void setSbdRepository(ISBDRepository sbdRepository) {
        this.sbdRepository = sbdRepository;
    }

    @Autowired
    private void setCountryCodeMapper(ICountryCodeMapper countryCodeMapper) {
        this.countryCodeMapper = countryCodeMapper;
    }
}
