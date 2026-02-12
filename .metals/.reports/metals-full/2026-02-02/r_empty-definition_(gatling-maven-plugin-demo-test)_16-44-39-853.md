file:///C:/Users/suppo/Desktop/Gatling/gatling-demo/src/test/scala/Recorder.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -io/gatling.
	 -scala/Predef.io.gatling.
offset: 61
uri: file:///C:/Users/suppo/Desktop/Gatling/gatling-demo/src/test/scala/Recorder.scala
text:
```scala
import io.gatling.recorder.GatlingRecorder
import io.gatling@@.recorder.config.RecorderPropertiesBuilder

object Recorder extends App {

	val props = RecorderPropertiesBuilder()
		.simulationsFolder(IDEPathHelper.mavenSourcesDirectory.toString)
		.resourcesFolder(IDEPathHelper.mavenResourcesDirectory.toString)
		.simulationPackage("computerdatabase")

	GatlingRecorder.fromMap(props.build, Some(IDEPathHelper.recorderConfigFile))
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 