CREATE TABLE ARKIV_DOKUMENTASJON (
    id            SERIAL PRIMARY KEY,
    dato          TIMESTAMP,
    opprettet_av  VARCHAR(50),
    kilde         VARCHAR(43),
    dokumentasjon VARCHAR(132000)
);
