/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */

databaseChangeLog {

    property name: 'statusColumn', value: 'status', dbms: 'derby'
    property name: 'statusColumn', value: 'yaba_daba_doo', dbms: 'mysql'

    // IMPORTANT: This includes all files in subdirectories recursively.
    includeAll path: 'migrate/', relativeToChangelogFile: true

}
