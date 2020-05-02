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

    include file: 'init_schema/20191201335406_addStatusToPeople.groovy',
            relativeToChangelogFile: true,
            context: 'requiredStuff'

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
