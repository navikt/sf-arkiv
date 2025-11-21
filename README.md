# sf-arkiv

`sf-arkiv` er en on-premise PostgreSQL-arkivløsning laget for å ivareta kravene i arkivloven. Løsningen brukes til å lagre **arkivverdig, men ikke journalpliktig** data fra Salesforce. Hvert Salesforce-team er selv ansvarlig for å overføre slikt materiale til `sf-arkiv`.

**Journalpliktig materiale skal ikke lagres her**, men håndteres gjennom **Joark** eller **P360**.

Selv om skylagring er tillatt, lagres Salesforce-data fysisk i Sverige. For å oppfylle kravet om at arkivverdig data skal ha en **kopi lagret i Norge**, ligger `sf-arkiv` on-prem i FSS-sonen.

---

## Teknisk informasjon

Tilgang til databasekonfigurasjon via `vault-iac`:  
👉 [`vault-iac` / `sf-arkiv.yml`](https://github.com/navikt/vault-iac/blob/c841af67d9e3044f145abb0cde22d3db607546bc/terraform/teams/teamcrm/apps/sf-arkiv.yml#L3)

Provisioning i `database-iac`:  
- **Preprod:** [`preprod-fss20.yml#L60`](https://github.com/navikt/database-iac/blob/50f025c5392dd997d1054fa1ca48da866629b87f/config/preprod-fss20.yml#L60)  
- **Prod:** [`prod-fss17-this-cluster-is-full.yml#L66`](https://github.com/navikt/database-iac/blob/50f025c5392dd997d1054fa1ca48da866629b87f/config/prod-fss17-this-cluster-is-full.yml#L66)

---

## Support

For bistand med databasen:  
Slack-kanal **#postgres** eller kontakt **Karl Heinz**

---
