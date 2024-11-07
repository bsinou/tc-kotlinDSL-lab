import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.buildFeatures.golang
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

// version indicates the TeamCity DSL version. In Nov 2024, with TC 2024.07.3, this is the supported version:
version = "2024.03"

// List of MySql Docker images to test
val mySqlImageTags: ArrayList<String>
    get() = arrayListOf(
        "mysql:latest",
        "mysql:8.4",
        "mysql:8.0",
        "mysql:5.7",
        "mariadb:11",
        "mariadb:10",
        "mariadb:10.6",
    )

// List of PgSQL Docker images to test
val pgSqlImageTags: ArrayList<String>
    get() = arrayListOf(
        "postgres:latest",
        "postgres:bulleyes",
        "postgres:alpine",
        "postgres:16.4",
        "postgres:15.8",
    )

val runPackages = "./idm/... ./broker/... ./data/... ./scheduler/... ./common/storage/sql/..."

// Define the build configurations
// `project()` is the main entry point to the configuration script.
// It is a function call, which takes as a parameter a block that represents the entire TeamCity project.
// In that block, we compose the structure of the project.
project {

    description = "Cells V5 Unit Tests for SQL DB"

    for (imgTag in mySqlImageTags) {
        buildType(MySqlUnitTests(imgTag))
    }

    for (imgTag in pgSqlImageTags) {
        buildType(PgSqlUnitTests(imgTag))
    }

}

// Define a "dynamic" build config for MySQL Unit tests
class MySqlUnitTests(imgTag: String) : BuildType({
    id("TestUnit_${imgTag}".toId())
    name = "MySQL Unit Tests for $imgTag"

    description = "Perform the tests against a specific version"
    maxRunningBuilds = 1

    vcs {
        root(AbsoluteId("Build_CellsHomeNext"))
    }

    params {
        // Skip default storages during the tests
        param("env.CELLS_TEST_SKIP_SQLITE", "true")
        // (no bolt / no bleve)
        // param("env.CELLS_TEST_SKIP_LOCAL_INDEX", "true")

        param("RUN_PACKAGES", runPackages)
        param("RUN_LOG_JSON", "true")
        param("RUN_TAGS", "storage")
        param("RUN_SINGLE_TEST_PATTERN", "")
    }

    steps {
        script {
            name = "Relaunch MySQL container"
            id = "relaunch_mysql_container"
            scriptContent = """
                echo -n "... Force removing existing mysql DB container: "
                docker rm -f mysqldb
                sleep 2
                echo "     ==> OK"
                
                echo "...  Pull and relaunch a new container"
                docker pull $imgTag
                docker run --name mysqldb -p 26999:3306 -e MYSQL_ROOT_PASSWORD=admin -d $imgTag
                sleep 2
                echo "     ==> OK"
            """.trimIndent()
        }

        script {
            name = "Wait for container"
            id = "wait_before_run"
            scriptContent = """               
                echo "...  Wait 60 second to insure that the container is correctly started"
                sleep 60
                echo "     ==> Done sleeping"
            """.trimIndent()
        }

        script {
            name = "Run Tests"
            id = "Run_Tests"
            scriptContent = """
                echo "... Launching TC Build from Kotlin DSL"

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
                
                go test %RUN_PACKAGES% ${'$'}{args} 
            """.trimIndent()
        }
        script {
            name = "Clean after tests"
            id = "Clean_after_tests"
            scriptContent = """
                echo "... Trying to force remove all containers"
                echo "     => will throw Warning for containers that have *not* been started"
                docker rm -f mysqldb
                
                echo "     ==> OK"
            """.trimIndent()
        }
    }

    features {
        golang {
            enabled = true
            testFormat = "json"
        }
    }
}
)

// Define a "dynamic" build config for MySQL Unit tests
class PgSqlUnitTests(imgTag: String) : BuildType({
    id("TestUnit_${imgTag}".toId())
    name = "PG SQL Unit Tests for $imgTag"

    description = "Perform the tests against a specific version of the PostgreSQL DB"
    maxRunningBuilds = 1

    vcs {
        root(AbsoluteId("Build_CellsHomeNext"))
    }

    params {
        // Skip default storages during the tests
        param("env.CELLS_TEST_SKIP_SQLITE", "true")
        // (no bolt / no bleve)
        // param("env.CELLS_TEST_SKIP_LOCAL_INDEX", "true")

        param("RUN_PACKAGES", runPackages)
        param("RUN_LOG_JSON", "true")
        param("RUN_TAGS", "storage")
        param("RUN_SINGLE_TEST_PATTERN", "")
    }

    steps {
        script {
            name = "Relaunch PG SQL container"
            id = "relaunch_pgsql_container"
            scriptContent = """
                echo -n "... Force removing existing PGSQL DB container: "
                docker rm -f pgsqldb
                sleep 2
                echo "     ==> OK"
                
                echo "...  Pull and relaunch a new container"
                docker pull $imgTag
                docker run --name pgsqldb -p 26998:5432 -e POSTGRES_USER=pydio -e POSTGRES_PASSWORD=cells -e POSTGRES_DB=testdb -d $imgTag
                sleep 2
                echo "     ==> OK"
            """.trimIndent()
        }

        script {
            name = "Wait for container"
            id = "wait_before_run"
            scriptContent = """               
                echo "...  Wait 60 second to insure that the container is correctly started"
                sleep 60
                echo "     ==> Done sleeping"
            """.trimIndent()
        }

        script {
            name = "Run Tests"
            id = "Run_Tests"
            scriptContent = """
                echo "... Launching TC Build from Kotlin DSL"

        	    host="localhost"
        	    port="26998"
        	    username="pydio"
        	    password="cells"
        	    dbname="testdb"
        	    dbdsn="postgres://${'$'}{username}:${'$'}{password}@${'$'}{host}:${'$'}{port}/${'$'}{dbname}?sslmode=disable"
        	    echo "... PGSQL DB URL: ${'$'}dbdsn"
        	    export CELLS_TEST_PGSQL_DSN="${'$'}dbdsn"
                               
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
                
                go test %RUN_PACKAGES% ${'$'}{args} 
            """.trimIndent()
        }
        script {
            name = "Clean after tests"
            id = "Clean_after_tests"
            scriptContent = """
                echo "... Trying to force remove the PGSQL container"
                echo "     => will throw Warning for containers that have *not* been started"
                docker rm -f pgsqldb
                
                echo "     ==> OK"
            """.trimIndent()
        }
    }

    features {
        golang {
            enabled = true
            testFormat = "json"
        }
    }
}
)

// First Hops based on https://blog.jetbrains.com/teamcity/2019/03/configuration-as-code-part-1-getting-started-with-kotlin-dsl/
/*
object PetClinicVcs : GitVcsRoot({
    name = "https://github.com/spring-projects/spring-petclinic#refs/heads/main"
    url = "https://github.com/spring-projects/spring-petclinic"
    branch = "refs/heads/main"
    branchSpec = "refs/heads/*"
    authMethod = password {
        userName = "bsinou"
        password = "credentialsJSON:4cf4a333-b1fa-43c7-abd1-3209a0a24c51"
    }
})

object MavenBuildPoc : BuildType({
    name = "Maven Build PoC"

    vcs {
        root(PetClinicVcs)
    }

    steps {
        script {
            name = "Test Command Line"
            scriptContent = """
                echo %build.number%
                echo "Another command"
                echo "Yet another command"
            """.trimIndent()
            scriptContent = "echo %build.number%"
        }
        maven {
            name = "Maven Build"
            id = "Maven2"
            goals = "clean test"
            runnerArgs = "-Dmaven.test.failure.ignore=true"
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
        swabra { }
    }
})
*/