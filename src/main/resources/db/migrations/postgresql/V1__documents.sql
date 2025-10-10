CREATE TABLE document
(
    id          UUID NOT NULL,
    document    BYTEA NOT NULL,
    instance    VARCHAR NOT NULL,       -- VAX instance/domain name
    created     DATE NOT NULL,          -- Tne time the document was received
    retrieved   DATE NULL,          -- The latest retrieved attempt from VAX (it can fail, and never be confirmed)
    confirmed   DATE NULL,          -- The confirmed data from VAX
    status      VARCHAR NOT NULL,       -- The current status of the document.
    externalId  VARCHAR NULL,       -- The id the document has in VAX
    CONSTRAINT pk_document PRIMARY KEY (id)
);

-- Missing index on status, instance, created.