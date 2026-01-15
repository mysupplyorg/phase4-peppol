package com.mysupply.phase4.persistence;

import com.mysupply.phase4.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ISBDRepository extends JpaRepository<Document, UUID> {
    // Find only document IDs by sender, receiver and domain that have not been retrieved
    @Query("SELECT d.id FROM Document d WHERE d.retrieved IS NULL " +
           "AND (:senderWildcard = true OR d.senderIdentifier IN :senderIdentifiers) " +
           "AND (:receiverWildcard = true OR d.receiverIdentifier IN :receiverIdentifiers) " +
           "AND (:domainWildcard = true OR d.domain IN :domains) " +
           "ORDER BY d.created ASC")
    List<UUID> findNotRetrievedIdsBySearchCriteria(
            @Param("senderWildcard") boolean senderWildcard,
            @Param("senderIdentifiers") List<String> senderIdentifiers,
            @Param("receiverWildcard") boolean receiverWildcard,
            @Param("receiverIdentifiers") List<String> receiverIdentifiers,
            @Param("domainWildcard") boolean domainWildcard,
            @Param("domains") List<String> domains
    );
}
