/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */

databaseChangeLog(context: 'infoStuff') {

    changeSet(id: '20191201335406', author: 'john') {
        addColumn(tableName: 'people') {
            column(name: '${statusColumn}', type: 'java.sql.Types.CHAR(5)')
        }
        addNotNullConstraint(tableName: 'people',
                columnName: 'status', columnDataType: 'java.sql.Types.CHAR(5)')
    }

}
