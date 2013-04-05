import grails.util.Environment
import grails.util.Holders

import java.lang.reflect.Modifier

import javax.faces.application.FacesMessage
import javax.faces.application.ProjectStage
import javax.faces.context.FacesContext
import javax.faces.webapp.FacesServlet

import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.plugins.GrailsPluginManager
import org.codehaus.groovy.grails.web.pages.GroovyPage
import org.codehaus.groovy.grails.web.pages.TagLibraryLookup
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.doc4web.grails.jsf.BeanArtefactHandler
import org.doc4web.grails.jsf.FacesUtils;
import org.doc4web.grails.jsf.RedirectDynamicMethod
import org.doc4web.grails.jsf.RenderDynamicMethod
import org.doc4web.grails.jsf.TagJsfResolver
import org.doc4web.grails.jsf.facelets.GrailsResourceResolver
import org.doc4web.grails.jsf.faces.GrailsHibernatePhaseListener
import org.doc4web.grails.jsf.faces.ViewScope
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.config.CustomScopeConfigurer
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.validation.Errors
import org.springframework.web.context.request.RequestContextHolder as RCH

import com.sun.faces.config.ConfigureListener

class Jsf2GrailsPlugin {
	// the plugin version
	def version = "0.2-SNAPSHOT"
	// the version or versions of Grails the plugin is designed for
	def grailsVersion = "2.2.0 > *"
	// the other plugins this plugin depends on
	def dependsOn = [:]
	// resources that are excluded from plugin packaging
	def pluginExcludes = [
			"grails-app/views/**",
			"web-app/**",
			"grails-app/controllers/**",
			"grails-app/conf/UrlMappings.groovy",
			"grails-app/conf/DataSource.groovy",
			"grails-app/domain/**"
	]

	def author = "Stephane MALDINI"
	def authorEmail = "smaldini@doc4web.com"
	def title = "JSF 2"
	def description = '''\\
h1. JSF-2 integration
* Bean By convention (no need for faces-config)
* Bean access to controller parameters (session,request,params...)
* Beans dynamic methods for navigation redirect(action?,bean?,view?,uri?,url?) and render(action?,view?,bean?),
* Automatic bean and service properties resolution
* 'void init()' called at bean initialization if present
* 'void dispose()' called at bean destruction if present
* JSF Extended request scope ('view')
* Access to web.xml configuration via Jsf2Config.groovy
* Access to faces-config generated in web-app/faces-config.xml
* Converters for Locale, Closure, Currency, Timezone
* i18n ready, fast access with #{m['key']}
* create-bean script
* Hibernate session managed from view rendering to view response
* Execute groovy code in EL expression ( #{gtag.groov[' def i = 2; 3.times{ i++ }; i; ']} )
* Support JSF2 Components - @ManagedBean ...

h2. Extra methods for beans :
* Support tagLibraries methods
* Support controllers properties
* redirect(action?,bean?,view?,uri?,url?),
* render(action?,view?,bean?)
* facesMessage(summary?, details?, severity?)

'''

	// URL to the plugin's documentation
	def documentation = "http://grails.org/plugin/jsf2"

	def observe = ["services", "i18n"]

	def loadAfter = ["controllers", "services"]

	def artefacts = [new BeanArtefactHandler()]

	def watchedResources = [
		"file:./grails-app/beans/**/*Bean.groovy",
		"file:./plugins/*/grails-app/beans/**/*Bean.groovy"
	]

	def config = Holders.config.grails.plugins.jsf2
	
	def doWithWebDescriptor = { xml ->

		def configuredContextParams = config.contextParams
		if (!configuredContextParams) {
			configuredContextParams = [
				[
					"param-name": "javax.faces.FACELETS_RESOURCE_RESOLVER",
					"param-value": GrailsResourceResolver.class.name
				],
				[
					"param-name": "javax.faces.PROJECT_STAGE",
					"param-value": translateEnvironment()
				] 
			]
			if (Environment.current.isReloadEnabled()) {
				configuredContextParams << [
					"param-name": "javax.faces.REFRESH_PERIOD",
					"param-value": "0"
				]
			}
		}
		xml."context-param"[0] + {
			configuredContextParams.each { configuredContextParam ->
				"context-param" {
					configuredContextParam.each {
						"${it.key}" "${it.value}"
					}
				}
			}
		}
		
		def configuredServlets = config.servlets
		if (!configuredServlets) {
			configuredServlets = [
				[
					"servlet-name": "FacesServlet",
					"servlet-class": FacesServlet.class.name,
					"load-on-startup": "1"
				]
			]
		}
		xml.servlet[-1] + {
			configuredServlets.each { configuredServlet ->
				servlet {
					configuredServlet.each {
						"${it.key}" "${it.value}"
					}
				}
			}
		}
		
		def configuredServletMappings = config.servletMappings
		if (!configuredServletMappings) {
			configuredServletMappings = [
				[
					"servlet-name": "FacesServlet",
					"url-pattern": "*.xhtml"
				]
			]
		}
		xml.servlet[-1] + {
			configuredServletMappings.each { configuredServletMapping ->
				"servlet-mapping" {
					configuredServletMapping.each {
						"${it.key}" "${it.value}"
					}
				}
			}
		}
		
		def configuredListeners = config.listeners
		if (!configuredListeners) {
			configuredListeners = [
				["listener-class": ConfigureListener.class.name]
			]
		}
		xml.listener[-1] + {
			configuredListeners.each { configuredListener ->
				listener {
					configuredListener.each {
						"${it.key}" "${it.value}"
					}
				}
			}
		}

	}

	def doWithSpring = {
		viewScope ViewScope

		customScopeConfigurer(CustomScopeConfigurer) {
			scopes = ['view': ref('viewScope')]
		}

		gtag(TagJsfResolver) {
			gspTagLibraryLookup = ref('gspTagLibraryLookup')
		}

		for (beanClazz in application.beanClasses) {

			GrailsClass beanClass = beanClazz

			"${beanClass.fullName}BeanClass"(MethodInvokingFactoryBean) {
				targetObject = ref("grailsApplication", true)
				targetMethod = "getArtefact"
				arguments = [BeanArtefactHandler.TYPE, beanClass.fullName]
			}
			
			String scope = beanClass.getPropertyValue("scope")
			def init = BeanUtils.findMethodWithMinimalParameters(beanClass.clazz, "init") ? true : false
			def dispose = BeanUtils.findMethodWithMinimalParameters(beanClass.clazz, "dispose") ? true : false

			"${beanClass.propertyName}"(beanClass.getClazz()) { bean ->
				if (scope) bean.scope = scope
				if (init) bean.initMethod = "init"
				if (dispose) bean.destroyMethod = "dispose"
			}
			
			if (manager.hasGrailsPlugin("hibernate")) {
				grailsHibernatePhaseListener(GrailsHibernatePhaseListener) {
					sessionFactory = ref("sessionFactory")
				}
			}
		}

	}

	def doWithDynamicMethods = { ctx ->

		GrailsPluginManager pluginManager = getManager()

		if (manager?.hasGrailsPlugin("groovyPages")) {
			TagLibraryLookup gspTagLibraryLookup = ctx.getBean("gspTagLibraryLookup") as TagLibraryLookup
			for (namespace in gspTagLibraryLookup.availableNamespaces) {
				def propName = GrailsClassUtils.getGetterName(namespace)
				def namespaceDispatcher = gspTagLibraryLookup.lookupNamespaceDispatcher(namespace)
				def beanClasses = application.beanClasses*.clazz
				for (Class beanClass in beanClasses) {
					MetaClass mc = beanClass.metaClass
					if (!mc.getMetaProperty(namespace)) {
						mc."$propName" = { namespaceDispatcher }
					}
					registerBeanMethodMissing(mc, gspTagLibraryLookup, ctx)
					Class superClass = beanClass.superclass
					// deal with abstract super classes
					while (superClass != Object.class) {
						if (Modifier.isAbstract(superClass.getModifiers())) {
							registerBeanMethodMissing(superClass.metaClass, gspTagLibraryLookup, ctx)
						}
						superClass = superClass.superclass
					}

				}
			}
		}

		for (bean in application.beanClasses) {
			MetaClass mc = bean.metaClass
			WebMetaUtils.registerCommonWebProperties(mc, application)
			registerBeanMethods(mc, ctx)
			mc.getPluginContextPath = {->
				pluginManager.getPluginPathForInstance(delegate) ?: ''
			}
		}
	}

	def onChange = { event ->

		if (event.source && event.source instanceof Class && application.isArtefactOfType(BeanArtefactHandler.TYPE, event.source as Class)) {
			def beanClass = application.addArtefact(BeanArtefactHandler.TYPE, event.source as Class)
			def beanName = "${beanClass.propertyName}"
			def scope = beanClass.getPropertyValue("scope")

			def init = BeanUtils.findMethodWithMinimalParameters(beanClass.clazz, "init") ? true : false
			def dispose = BeanUtils.findMethodWithMinimalParameters(beanClass.clazz, "dispose") ? true : false

			def beans = beans {
				"$beanName"(beanClass.getClazz()) { bean ->
					if (scope) bean.scope = scope
					if (init) bean.initMethod = "init"
					if (dispose) bean.destroyMethod = "dispose"
				}
			}

			event.ctx.getBean('viewScope').timestamp = System.currentTimeMillis()

			beans.registerBeans(event.ctx)
			event.manager?.getGrailsPlugin("jsf2")?.doWithDynamicMethods(event.ctx)
		}
	}

	def onConfigChange = { event ->

	}

	private String translateEnvironment() {
		switch (Environment.current.name) {
			case Environment.DEVELOPMENT: return ProjectStage.Development.name()
			case Environment.TEST: return ProjectStage.SystemTest.name()
			default: return ProjectStage.Production.name()
		}
	}

	def registerBeanMethods(MetaClass mc, ApplicationContext ctx) {

		mc.el { String expr ->
			FacesContext fc = FacesContext.currentInstance
			return fc?.application?.evaluateExpressionGet(fc, expr, Object.class)
		}

		mc.facesMessage { Map args ->
			FacesContext.currentInstance.addMessage(args.id, new FacesMessage(args.summary, args.details, args.severity))
		}
		mc.facesMessage { String id, String msg ->
			FacesContext.currentInstance.addMessage(id, new FacesMessage(msg))
		}

		mc.getViewUri = { String name ->
			def webRequest = RCH.currentRequestAttributes()
			webRequest.attributes.getViewUri(name, webRequest.currentRequest)
		}
		
		mc.translateErrors = { Errors errors, String formId ->
			FacesUtils.translateErrors errors, formId
		}
		
		mc.setErrors = { Errors errors ->
			RCH.currentRequestAttributes().setAttribute(GrailsApplicationAttributes.ERRORS, errors, 0)
		}
		mc.getErrors = {->
			RCH.currentRequestAttributes().getAttribute(GrailsApplicationAttributes.ERRORS, 0)
		}

		mc.hasErrors = {->
			errors?.hasErrors() ? true : false
		}

		def redirect = new RedirectDynamicMethod(ctx)

		mc.redirect = { Map args ->
			redirect.invoke(delegate, "redirect", args)
		}

		mc.redirect = { String arg ->
			redirect.invoke(delegate, "redirect", [action: arg])
		}

		def render = new RenderDynamicMethod(ctx)

		mc.render = { Map args ->
			render.invoke(delegate, "render", args)
		}

		mc.render = { String arg ->
			render.invoke(delegate, "render", [action: arg])
		}

	}

	static def registerBeanMethodMissing(MetaClass mc, TagLibraryLookup lookup, ApplicationContext ctx) {
		// allow controllers to call tag library methods
		mc.methodMissing = { String name, args ->
			args = args == null ? [] as Object[] : args
			def tagLibrary = lookup.lookupTagLibrary(GroovyPage.DEFAULT_NAMESPACE, name)
			if (tagLibrary) {
				MetaClass controllerMc = delegate.class.metaClass
				WebMetaUtils.registerMethodMissingForTags(controllerMc, lookup, GroovyPage.DEFAULT_NAMESPACE, name)
				if (controllerMc.respondsTo(delegate, name, args)) {
					return controllerMc.invokeMethod(delegate, name, args)
				}
				else {
					throw new MissingMethodException(name, delegate.class, args)
				}
			}
			else {
				throw new MissingMethodException(name, delegate.class, args)
			}
		}

	}

}
