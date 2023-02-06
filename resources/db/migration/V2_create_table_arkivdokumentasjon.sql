CREATE TABLE ARKIV_DOKUMENTASJON_V2 (
    id            SERIAL PRIMARY KEY,
    dato          TIMESTAMP,
    opprettet_av  VARCHAR(50),
    kilde         VARCHAR(43),
    dokumentasjon VARCHAR(132000),
    dokumentdato  TIMESTAMP,
    fnr           VARCHAR(13),
    orgnr         VARCHAR(13),
    tema          VARCHAR(50)
);
