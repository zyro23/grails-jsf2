package org.doc4web.grails.jsf

import grails.util.Holders

import javax.faces.application.FacesMessage
import javax.faces.context.FacesContext

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.FieldError

class FacesUtils {

	static void translateErrors(Object bean, String formId) {
		bean.errors?.allErrors?.each {
			FacesContext.currentInstance.addMessage(
				it in FieldError ? "${formId}:${it.field}" : "${formId}",
				new FacesMessage(
					FacesMessage.SEVERITY_ERROR,
					Holders.grailsApplication.mainContext.messageSource.getMessage(it, LocaleContextHolder.locale),
					null
				)
			)
		}
	}
	
}
