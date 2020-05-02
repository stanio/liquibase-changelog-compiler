/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */

databaseChangeLog {

    changeSet(id: '20191202435365', author: 'saly', logicalFilePath: 'foo_bar') {
        addColumn(tableName: 'people') {
            column(name: 'created_at', type: 'java.sql.Types.TIMESTAMP',
                                       defaultValueComputed: 'CURRENT_TIMESTAMP') {
                constraints(nullable: false)
            }
            column(name: 'updated_at', type: 'java.sql.Types.TIMESTAMP',
                                       defaultValueComputed: 'CURRENT_TIMESTAMP') {
                constraints(nullable: false)
            }
        }
    }

}
