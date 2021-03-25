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

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.annotations.Nullable
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic.Kind.*
import javax.tools.StandardLocation
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName

/**
 * Base class for implementing a [javax.annotation.processing.Processor].
 */
abstract class KGenerator : AbstractProcessor() {

    private val debug: Boolean = "true" == System.getProperty("org.litote.kgenerator.debug")

    val env: ProcessingEnvironment get() = processingEnv

    open val notSupportedModifiers: Set<Modifier> = setOf(Modifier.STATIC, Modifier.TRANSIENT)

    override fun getSupportedSourceVersion(): SourceVersion {
        debug { SourceVersion.latest() }
        return SourceVersion.latest()
    }

    inline fun <reified T : Annotation, reified R : Annotation> getAnnotatedClasses(roundEnv: RoundEnvironment): AnnotatedClassSet {
        val dataElements = roundEnv.getElementsAnnotatedWith(T::class.java)
            .map { AnnotatedClass(this, it as TypeElement, hasInternalModifier<T>(it)) }
        val registryElements = getRegistryClasses<R>(roundEnv)

        debug { registryElements }
        debug { dataElements }

        return AnnotatedClassSet(
            (dataElements + registryElements)
                .toMutableSet()
        ).apply {
            if (isNotEmpty()) {
                log("Found ${T::class.simpleName} classes: $this")
            }
        }
    }

    inline fun <reified T : Annotation> hasInternalModifier(element: Element): Boolean {
        return element
            .annotationMirrors
            .first { it.annotationType.toString() == T::class.qualifiedName }
            .elementValues
            .run {
                keys.find { it.simpleName.toString() == "internal" }
                    ?.let { get(it)?.value as? Boolean }
            } ?: false
    }

    inline fun <reified T : Annotation> getRegistryClasses(roundEnv: RoundEnvironment): Set<AnnotatedClass> =
        roundEnv
            .getElementsAnnotatedWith(T::class.java)
            .map { element ->
                element.annotationMirrors
                    .first { it.annotationType.toString() == T::class.qualifiedName }
            }
            .flatMap { a ->
                val internal = a.elementValues[a.elementValues.keys.find {
                    it.simpleName.toString() == "internal"
                }]?.value as? Boolean ?: false

                a.elementValues[a.elementValues.keys.first {
                    it.simpleName.toString() == "value"
                }]
                    .let { v ->
                        @Suppress("UNCHECKED_CAST")
                        (v!!.value as Iterable<AnnotationValue>).map {
                            AnnotatedClass(
                                this,
                                env.typeUtils.asElement(it.value as TypeMirror) as TypeElement,
                                internal
                            )
                        }
                    }
            }
            .toSet()

    //see https://github.com/square/kotlinpoet/issues/236
    fun javaToKotlinType(element: Element): TypeName =
        javaToKotlinType(element.asType())

    fun javaToKotlinType(typeMirror: TypeMirror): TypeName =
        javaToKotlinType(typeMirror.asTypeName())

    fun asTypeName(element: Element): TypeName {
        val annotation = element.getAnnotation(Nullable::class.java)
        val typeName = element.asType().asTypeName()
        return if (annotation != null) typeName.copy(nullable = true) else typeName
    }

    fun asTypeName(type: TypeMirror): TypeName {
        val annotation =
            type.getAnnotation(Nullable::class.java) ?: processingEnv.typeUtils.asElement(type)?.getAnnotation(Nullable::class.java)
        return type.asTypeName().let { if (annotation != null) it.copy(nullable = true) else it }
    }

    fun javaToKotlinType(typeName: TypeName): TypeName =
        with(typeName) {
            when (this) {
                is ParameterizedTypeName -> {
                    val raw = javaToKotlinType(rawType) as ClassName
                    if (raw.toString() == "kotlin.Array" && typeArguments.firstOrNull()?.let { javaToKotlinType(it) }?.toString() == "kotlin.Byte") {
                        ClassName.bestGuess("kotlin.ByteArray")
                    } else {
                        raw.parameterizedBy(
                            *typeArguments.map { javaToKotlinType(it) }.toTypedArray()
                        )
                    }
                }
                is WildcardTypeName -> {
                    if (outTypes.isNotEmpty()) {
                        debug { "out: $outTypes" }
                        WildcardTypeName.producerOf(javaToKotlinType(outTypes.first()).copy(nullable = true))
                    } else {
                        debug { "in: $inTypes" }
                        WildcardTypeName.consumerOf(javaToKotlinType(inTypes.first()))
                    }
                }
                else -> {
                    debug { "class: $typeName" }
                    val className = JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(FqName(toString()))
                        ?.asSingleFqName()?.asString()

                    if (className == null) {
                        typeName
                    } else {
                        ClassName.bestGuess(className)
                    }.also {
                        debug { it }
                    }
                }
            }
        }

    fun debug(f: () -> Any?) {
        if (debug) {
            log(f())
        }
    }

    fun log(value: Any?) {
        processingEnv.messager.printMessage(NOTE, value.toString())
    }

    fun log(t: Throwable) {
        processingEnv.messager.printMessage(
            NOTE,
            t.run {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                printStackTrace(pw)
                sw.toString()
            })
    }

    fun warn(value: Any?) {
        processingEnv.messager.printMessage(WARNING, value.toString())
    }

    fun error(t: Throwable) {
        processingEnv.messager.printMessage(
            ERROR,
            t.run {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                printStackTrace(pw)
                sw.toString()
            })
    }


    fun enclosedCollectionPackage(type: TypeMirror): String =
        processingEnv.elementUtils.getPackageOf(
            if (type is ArrayType) {
                processingEnv.typeUtils.asElement(type.componentType)
            } else {
                processingEnv.typeUtils.asElement((type as DeclaredType).typeArguments.first())
            }
        ).qualifiedName.toString()

    fun enclosedValueMapPackage(type: TypeMirror): String =
        processingEnv.elementUtils.getPackageOf(
            processingEnv.typeUtils.asElement((type as DeclaredType).typeArguments[1])
        ).qualifiedName.toString()

    fun mapKeyClass(type: TypeMirror, annotatedMap: Boolean): TypeName? =
        if (annotatedMap) asTypeName((type as DeclaredType).typeArguments[0])
        else null

    fun writeFile(fileBuilder: FileSpec.Builder, location: StandardLocation = StandardLocation.SOURCE_OUTPUT) {
        try {
            val kotlinFile = fileBuilder.build()
            debug {
                processingEnv.filer.getResource(
                    location,
                    kotlinFile.packageName,
                    kotlinFile.name
                ).name
            }

            kotlinFile.writeTo(
                Paths.get(
                    processingEnv.filer.getResource(
                        location,
                        "",
                        kotlinFile.name
                    ).toUri()
                ).parent
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    fun writeFile(
        outputDirectory: String,
        file: String,
        content: String,
        location: StandardLocation = StandardLocation.SOURCE_OUTPUT,
        failOnError: Boolean = true
    ) {
        try {
            val directory = Paths.get(
                processingEnv.filer.getResource(
                    location,
                    "",
                    outputDirectory
                ).toUri()
            )
            debug { directory }
            Files.createDirectories(directory)
            val outputPath = directory.resolve(file)
            debug { outputPath }
            OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8).use { writer ->
                writer.append(
                    content
                )
            }
        } catch (e: Exception) {
            log("Error writing $file in $outputDirectory:\n$content")
            if (failOnError) error(e) else log(e)
        }
    }

    fun findByProperty(sourceClassName: TypeName, targetElement: TypeName, propertyName: String): CodeBlock =
        CodeBlock.builder().add(
            "org.litote.kreflect.findProperty<%1T,%2T>(%3S)",
            sourceClassName,
            targetElement,
            propertyName
        ).build()

    fun findPropertyValue(
        sourceClassName: TypeName,
        targetElement: TypeName,
        owner: String,
        propertyName: String
    ): CodeBlock =
        CodeBlock.builder().add(
            "org.litote.kreflect.findPropertyValue<%1T,%2T>(%3L, %4S)",
            sourceClassName,
            targetElement,
            owner,
            propertyName
        ).build()

    fun setPropertyValue(
        sourceClassName: TypeName,
        targetElement: TypeName,
        owner: String,
        propertyName: String,
        newValue: String
    ): CodeBlock =
        CodeBlock.builder().add(
            "org.litote.kreflect.setPropertyValue<%1T,%2T>(%3L, %4S, %5L)",
            sourceClassName,
            targetElement,
            owner,
            propertyName,
            newValue
        ).build()

    /**
     * True if the property type is a Collection.
     */
    fun isCollection(element: Element): Boolean =
        element.asType() is DeclaredType
                && env.typeUtils.isAssignable(
            env.typeUtils.erasure(element.asType()),
            env.elementUtils.getTypeElement("java.util.Collection").asType()
        )

    /**
     * True if the property type is a Map.
     */
    fun isMap(element: Element): Boolean =
        element.asType() is DeclaredType
                && env.typeUtils.isAssignable(
            env.typeUtils.erasure(element.asType()),
            env.elementUtils.getTypeElement("java.util.Map").asType()
        )

    /**
     * Returns the parameter type with [Element] format if any.
     */
    fun typeArgumentElement(element: Element, index: Int = 0): Element? =
        (element.asType() as? DeclaredType)
            ?.run { typeArguments.getOrNull(index) }
            ?.let { env.typeUtils.asElement(it) }

    /**
     * Returns the parameter type with [ReflectedType] format if any.
     */
    fun typeArgumentReflected(element: Element, index: Int = 0): ReflectedType? =
        typeArgumentReflected(element.asType(), index)

    /**
     * Returns the parameter type with [ReflectedType] format if any.
     */
    fun typeArgumentReflected(typeMirror: TypeMirror, index: Int = 0): ReflectedType? =
        ((typeMirror as? DeclaredType) ?: (typeMirror as ReflectedType).typeMirror as? DeclaredType)
            ?.run { typeArguments.getOrNull(index) }
            ?.let { ReflectedType(this, it) }

}
