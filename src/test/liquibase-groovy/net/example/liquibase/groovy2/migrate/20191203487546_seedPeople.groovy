/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */

databaseChangeLog(logicalFilePath: 'seed_the_people', context: 'fullStuff') {

    changeSet(id: '20191203487546', author: 'peter') {
        loadData(tableName: 'people', file: 'net/example/liquibase/groovy/migrate/people.csv') {
            // Please use BOOLEAN, NUMERIC, DATE, STRING, COMPUTED, SEQUENCE, UUID or SKIP
            column(name: 'name', type: 'STRING')
            column(name: 'age', type: 'NUMERIC')
            column(name: 'status', type: 'STRING')
        }
        rollback { /* dummy */ }
    }

}
