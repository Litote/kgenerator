package org.litote.kgenerator

import javax.lang.model.element.Element

/**
 * A set of [AnnotatedClass].
 */
class AnnotatedClassSet(
    private val elements: MutableSet<AnnotatedClass> = mutableSetOf()
) {

    fun isNotEmpty(): Boolean = elements.isNotEmpty()

    fun forEach(action: (AnnotatedClass) -> Unit) {
        elements.toList().forEach(action)
    }

    fun contains(element: Element?): Boolean = elements.any { it.element == element }

    fun add(element: AnnotatedClass) {
        elements.add(element)
    }

    fun filter(predicate: (AnnotatedClass) -> Boolean): AnnotatedClassSet {
        return AnnotatedClassSet(elements.filter(predicate).toMutableSet())
    }

    fun toList(): List<AnnotatedClass> = elements.toList()

    override fun toString(): String = elements.toString()
}