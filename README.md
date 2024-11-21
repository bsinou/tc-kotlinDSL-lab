# tc-kotlinDSL-lab

Test TC configuration as code

## Usage

### Modify an existing build

- modify the Kotlin code
- commit and push
- edit the corresponding parent project in TC (e.g. `Kotlin DSL Lab`)
- open the `Versioned Setting` tab
- click on the `Load project settings from VCS...` button to insure the code can be imported
- launch one of the child build

## First Hops

This project is a lab that was initially based on [this tutorial](https://blog.jetbrains.com/teamcity/2019/03/configuration-as-code-part-1-getting-started-with-kotlin-dsl) on JetBrain's blog.

**Tip**: To get DSL API documentation in the IDE, run:

```shell
# if  necessary
sudo apt install maven

cd .teamcity
mvn -U dependency:sources
```

You might also want to right-click on the corresponding pom.xml file and choose "Define as maven project".

## References

- The  current API understood by our server: https://<your TC server URL>/app/dsl-documentation/index.html
- The documentation on Jetbrains website (with first hops): https://www.jetbrains.com/help/teamcity/kotlin-dsl.html?Kotlin+DSL
- The official API: https://teamcity.jetbrains.com/app/dsl-documentation/index.html
- About the  KotlinDSL doc:  https://dev.to/teamcity/kotlin-dsl-examples-in-teamcity-3n24
