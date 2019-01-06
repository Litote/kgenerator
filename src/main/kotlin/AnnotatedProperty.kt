/*
 * Copyright (C) 2018 Litote
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.litote.kgenerator

import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import javax.lang.model.element.Element
import javax.lang.model.element.VariableElement

/**
 * An annotated property of an [AnnotatedClass].
 */
class AnnotatedProperty(
    private val generator: KGenerator,
    private val element: VariableElement,
    val getter: Element?,
    val parameter: VariableElement?
) : VariableElement by element {

    private val env = generator.env

    /**
     * Gets the specified annotation if any.
     */
    inline fun <reified T : Annotation> getAnnotation(): T? =
        getAnnotation(T::class.java)
                ?: getter?.getAnnotation(T::class.java)
                ?: parameter?.getAnnotation(T::class.java)

    /**
     * Returns true if the property is annotated with the specified annotation.
     */
    inline fun <reified T : Annotation> hasAnnotation(): Boolean = getAnnotation<T>() != null

    /**
     * True if the property type is a Collection.
     */
    val isCollection: Boolean get() = generator.isCollection(this)

    /**
     * True if the property type is a Map.
     */
    val isMap: Boolean get() = generator.isMap(this)

    /**
     * The [TypeName] of the property.
     */
    val type: TypeName get() = generator.javaToKotlinType(element)

    /**
     * The [ReflectedType] of the property.
     */
    val reflectedType : ReflectedType = ReflectedType(generator, asType())

    /**
     * Returns the parameter type with [Element] format if any.
     */
    fun typeArgumentElement(index: Int = 0): Element? = generator.typeArgumentElement(this, index)

    /**
     * Returns the parameter type with [TypeName] format if any.
     */
    fun typeArgument(index: Int = 0): TypeName? =
        (type as? ParameterizedTypeName)?.typeArguments?.getOrNull(index)

}