/*
 * This module, both source code and documentation,
 * is in the Public Domain, and comes with NO WARRANTY.
 */

databaseChangeLog {

    changeSet(id: '20191201222341', author: 'john') {
        createTable(tableName: 'people') {
            column(name: 'id', type: 'java.sql.Types.INTEGER', autoIncrement: true) {
                constraints(primaryKey: true)
            }
            column(name: 'name', type: 'java.sql.Types.VARCHAR(50)') {
                constraints(nullable: false)
            }
            column(name: 'age', type: 'java.sql.Types.INTEGER') {
                constraints(nullable: false)
            }
        }
    }

}
