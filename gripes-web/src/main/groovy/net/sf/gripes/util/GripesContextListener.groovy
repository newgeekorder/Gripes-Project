package net.sf.gripes.util

import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

import org.mortbay.jetty.servlet.FilterMapping
import org.mortbay.jetty.servlet.FilterHolder

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GripesContextListener  implements ServletContextListener {
	Logger logger = LoggerFactory.getLogger(GripesContextListener.class)
	
	def context
	
	@Override void contextInitialized(ServletContextEvent contextEvent) {
		logger.info "Loading the Gripes Application..."
		context = contextEvent.getServletContext()
		
		def pack = context.getInitParameter("GripesPackage")+".model"
/*		(new File(this.class.classLoader.getResource(pack.replace(".","/")).getFile())).listFiles().each{
			if(it.isFile()) {
				def klass = Class.forName(pack.replace("/",".")+"."+it.name.replace(".class",""))
				if(klass && klass.getAnnotation(javax.persistence.Entity)){
					klass.metaClass.static.methodMissing = {String name, args ->
						klass.newInstance().methodMissing(name, args)
					}
				}
			}
		}*/
		
		def tempStr
		try { tempStr = context.TEMPDIR } 
		catch(e) { tempStr = context.getRealPath("/")+"/WEB-INF/work" }		
		def tempDir = new File(tempStr)
		if(!tempDir.exists()){
			tempDir.mkdirs()
			tempDir.deleteOnExit()
		}
		System.setProperty("gripes.temp", tempDir.toString())
		
		// TODO need to compensate for the Catalina method of implementing these Filters
		// TODO only use the /gripes-addons/ directory when addon is config'd with "-src"
		def gripesConfig = new ConfigSlurper().parse(this.class.classLoader.getResource("Config.groovy").text)
		gripesConfig.addons.each {
			def addonName = it
			def addonConfig = this.class.classLoader.getResource("gripes/addons/${addonName}/gripes.addon")
			if(!addonConfig){
				addonConfig = this.class.classLoader.getResource("gripes/gripes-addons/${addonName}/gripes.addon")
			}
			
			def addon = new ConfigSlurper().parse(addonConfig)
			addon.filters.each {k,v ->
				def filterConfig = v
				def holder = context.contextHandler.servletHandler.getFilter(k)
				logger.debug "Attaching the {} addon to the {}", addonName, k
				if(!holder){
					holder = new FilterHolder(
						heldClass : Class.forName(filterConfig.classname),
						name : k
					)
					def mapping= new FilterMapping(
						filterName : k,
						pathSpec : filterConfig.map,
						dispatches : FilterHolder.dispatch(filterConfig.dispatch)
					)

					context.contextHandler.servletHandler.addFilter(holder,mapping)	
				}	
				filterConfig.params.each {kk,vv ->
					logger.debug "The {} addon is updating the {} param for the {}", addonName, kk, k
					if(vv.startsWith("+")) 
						holder.setInitParameter(kk,holder.getInitParameter(kk)+","+vv[1..vv.length()-1])
					else
						holder.setInitParameter(kk,vv)
				}
			}
		}
	}
	
	@Override void contextDestroyed(ServletContextEvent contextEvent) {
		context = contextEvent.getServletContext()
		
		logger.info "Gripes Application Shutdown."
	}
}