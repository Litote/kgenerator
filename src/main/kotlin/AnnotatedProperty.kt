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