package org.litote.kgenerator

import com.squareup.kotlinpoet.TypeName
import javax.lang.model.type.TypeMirror

/**
 * Wrap a [TypeMirror].
 */
class ReflectedType(
    private val generator: KGenerator,
    internal val typeMirror: TypeMirror
) : TypeMirror by typeMirror {

    private val env = generator.env

    /**
     * True if the property type is a Collection.
     */
    val isCollection: Boolean get() = generator.isCollection(env.typeUtils.asElement(typeMirror))

    /**
     * True if the property type is a Map.
     */
    val isMap: Boolean get() = generator.isMap(env.typeUtils.asElement(typeMirror))

    /**
     * The [TypeName] of the property.
     */
    val type: TypeName get() = generator.javaToKotlinType(typeMirror)
}