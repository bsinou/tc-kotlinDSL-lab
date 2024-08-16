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
    }
}))

