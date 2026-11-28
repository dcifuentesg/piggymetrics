package com.piggymetrics.notification.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identificador del caso de prueba (p. ej. "RSI-09").
 * {@link VerdictListener} lo usa para reportar un veredicto por caso.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestCaseId {
	String value();
}
