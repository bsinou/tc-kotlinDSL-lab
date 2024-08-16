package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, create a buildType with id = 'UnitTestsPoc'
in the root project, and delete the patch script.
*/
create(DslContext.projectId, BuildType({
    id("UnitTestsPoc")
    name = "Test Unit Tests Config"
    description = "Try a real life example"

    params {
        param("RUN_LOG_JSON", "false")
        param("RUN_TAGS", "storage")
        param("RUN_PACKAGES", "./idm/...")
        param("RUN_SINGLE_TEST_PATTERN", "")
    }

    vcs {
        root(AbsoluteId("Build_CellsHomeNext"))
    }

    steps {
        script {
            name = "Relaunch container"
            id = "Relaunch_container"
            scriptContent = """
                echo -n "... Force removing existing mysql DB container: "
                docker rm -f mysqldb
                sleep 2
                echo "     ==> OK"
                
                echo "...  Pull and relaunch a new container"
                docker pull mysql:latest
                docker run --name mysqldb -p 26999:3306 -e MYSQL_ROOT_PASSWORD=admin -d mysql:latest
                sleep 2
                echo "     ==> OK"
            """.trimIndent()
        }
        script {
            name = "Run Tests"
            id = "Run_Tests"
            scriptContent = """
                host="localhost"
                port="26999"
                username="root"
                password="admin"
                dbname="testdb"
                
                # We must create the DB before launching the tests
                cmd="mysql -h ${'$'}{host} -P ${'$'}{port} --protocol=TCP -u${'$'}{username} -p${'$'}{password}"
                ${'$'}cmd -e "CREATE DATABASE IF NOT EXISTS testdb;"
                dburl="mysql://${'$'}{username}:${'$'}{password}@tcp(${'$'}{host}:${'$'}{port})/${'$'}{dbname}"
                echo "... MySQL DB URL: ${'$'}dburl"
                mysql_urls="${'$'}dburl"
                
                export CELLS_TEST_MYSQL_DSN="${'$'}mysql_urls"
                
                
                echo "... Listing test ENV:"
                printenv | grep CELLS_TEST
                
                export GOROOT=/usr/local/go22
                export PATH=${'$'}GOROOT/bin:${'$'}PATH
                export GOFLAGS="-count=1 -tags=%RUN_TAGS%"
                
                # Base argument for this build
                args="-v"
                
                if [ "true" = "%RUN_LOG_JSON%"  ]; then
                   # Integrate with TC by using json output
                   args="${'$'}{args} -json"
                fi
                
                if [ ! "xxx" = "xxx%RUN_SINGLE_TEST_PATTERN%"  ]; then
                	args="${'$'}{args} -run %RUN_SINGLE_TEST_PATTERN%"
                fi
                
                echo "... Launch command:"
                echo "go test %RUN_PACKAGES% ${'$'}{args}"
                
                go test -v %RUN_PACKAGES% ${'$'}{args}
            """.trimIndent()
        }
    }
}))

