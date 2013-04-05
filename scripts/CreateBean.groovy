includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsCreateArtifacts")

target('default': "Creates a new JavaServerFaces Bean") {
  depends parseArguments

  def type = "Bean"
  promptForName type: type

  def name = argsMap["params"][0]
  createArtifact name: name, suffix: type, type: type, path: "grails-app/beans"
  createUnitTest name: name, suffix: type
}