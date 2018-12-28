package org.litote.kgenerator

import javax.lang.model.element.Element
import javax.lang.model.element.VariableElement

/**
 * An annotated property of an [AnnotatedClass].
 */
class AnnotatedProperty(
    private val element: VariableElement,
    val getter: Element?,
    val parameter: VariableElement?
    ) : VariableElement by element {

    inline fun <reified T : Annotation> getAnnotation(): T? =
        getAnnotation(T::class.java)
                ?: getter?.getAnnotation(T::class.java)
                ?: parameter?.getAnnotation(T::class.java)

    inline fun <reified T : Annotation> hasAnnotation(): Boolean =
        getAnnotation<T>() != null

}