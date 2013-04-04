eventCompileStart = {

ant.copy(
	file: "${jsf2PluginDir}/src/templates/faces-config.xml",
	todir: "${basedir}/web-app/WEB-INF"
)
ant.copy(
	file: "${jsf2PluginDir}/src/templates/web.xml",
	todir: "${basedir}/web-app/WEB-INF"
)

}
