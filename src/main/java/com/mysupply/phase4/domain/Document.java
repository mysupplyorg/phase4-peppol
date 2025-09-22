package com.mysupply.phase4.domain;

import com.mysupply.phase4.persistence.DocumentConstants;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = DocumentConstants.DOCUMENT_TABLE_NAME, schema = DocumentConstants.DOCUMENT_SCHEMA_NAME)
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    private byte[] document;
    private Instant created;
    private DocumentStatus status;
    private String domain;

    protected Document() {

    }

    public Document(byte[] document) {
        this.document = document;
        this.created = Instant.now();
        this.status = DocumentStatus.Created;
        this.domain = "??";
    }

    public byte[] getDocument() {
        return document;
    }

    public void setDocument(byte[] document) {
        this.document = document;
    }

    public UUID getId() {
        return id;
    }
}
