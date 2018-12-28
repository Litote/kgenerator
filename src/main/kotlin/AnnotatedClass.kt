package org.litote.kgenerator

import com.squareup.kotlinpoet.CodeBlock
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

/**
 * An annotated class.
 */
class AnnotatedClass(
    private val generator: KGenerator,
    val element: TypeElement,
    val internal: Boolean
) : TypeElement by element {

    fun getPackage(): String =
        generator.env.elementUtils.getPackageOf(element).qualifiedName.toString()

    fun properties(selector: (AnnotatedProperty) -> Boolean = { true }): List<AnnotatedProperty> {
        val constructor =
            element.enclosedElements.firstOrNull { it.kind == ElementKind.CONSTRUCTOR } as? ExecutableElement
        return element.enclosedElements
            .asSequence()
            .filterIsInstance<VariableElement>()
            .map { variable ->
                AnnotatedProperty(
                    variable,
                    findGetter(variable.simpleName.toString()),
                    constructor?.parameters?.find { variable.simpleName.toString() == it.simpleName.toString() }
                )
            }
            .filter { e ->
                e.modifiers.none { generator.notSupportedModifiers.contains(it) }
                        && selector.invoke(e)
            }
            .toList()
    }

    private fun findGetter(name: String): Element? =
        element
            .enclosedElements
            .filterIsInstance<ExecutableElement>()
            .firstOrNull {
                it.simpleName.toString() == "get${name.capitalize()}"
            }


    fun propertyReference(
        property: Element,
        privateHandler: () -> CodeBlock,
        nonPrivateHandler: () -> CodeBlock
    ): CodeBlock {
        return if (
            findGetter(property.simpleName.toString())
                ?.modifiers
                ?.contains(Modifier.PRIVATE) != false
        ) {
            privateHandler()
        } else {
            nonPrivateHandler()
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is AnnotatedClass) {
            element == other.element
        } else {
            element == other
        }
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }

    override fun toString(): String {
        return "Annotated(element=$element, internal=$internal)"
    }

}