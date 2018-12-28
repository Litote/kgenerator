package org.litote.kgenerator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
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
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.NOTE
import javax.tools.Diagnostic.Kind.WARNING
import javax.tools.StandardLocation
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName

/**
 * A base class to subclass in order to implement a [javax.annotation.processing.Processor].
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
        javaToKotlinType(element.asType().asTypeName())

    fun asTypeName(element: Element): TypeName {
        val annotation = element.getAnnotation(Nullable::class.java)
        val typeName = element.asType().asTypeName()
        return if (annotation != null) typeName.copy(nullable = true) else typeName
    }

    fun javaToKotlinType(typeName: TypeName): TypeName =
        with(typeName) {
            when (this) {
                is ParameterizedTypeName -> {
                    val raw = javaToKotlinType(rawType) as ClassName
                    if (raw.toString() == "kotlin.Array" && typeArguments.firstOrNull()?.let { javaToKotlinType(it) }?.toString() == "kotlin.Byte") {
                        ClassName.bestGuess("kotlin.ByteArray")
                    } else {
                        raw.parameterizedBy(*typeArguments.map { javaToKotlinType(it) }.toTypedArray())
                    }
                }
                is WildcardTypeName -> {
                    if (outTypes.isNotEmpty()) {
                        WildcardTypeName.producerOf(javaToKotlinType(outTypes.first()))
                    } else {
                        WildcardTypeName.consumerOf(javaToKotlinType(inTypes.first()))
                    }
                }
                else -> {
                    debug { typeName }
                    debug { typeName.javaClass }
                    val className = JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(FqName(toString()))
                        ?.asSingleFqName()?.asString()
                    debug { className }
                    if (className == null) {
                        typeName
                    } else {
                        ClassName.bestGuess(className)
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

    fun firstTypeArgument(element: Element): TypeName =
        (javaToKotlinType(element) as ParameterizedTypeName).typeArguments.first()

    fun secondTypeArgument(element: Element): TypeName =
        (javaToKotlinType(element) as ParameterizedTypeName).typeArguments[1]

    fun mapKeyClass(type: TypeMirror, annotatedMap: Boolean): TypeName? =
        if (annotatedMap) asTypeName(processingEnv.typeUtils.asElement((type as DeclaredType).typeArguments[0]) as TypeElement)
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
        location: StandardLocation = StandardLocation.SOURCE_OUTPUT
    ) {
        try {
            val directory = Paths.get(
                processingEnv.filer.getResource(
                    location,
                    "",
                    outputDirectory
                ).toUri()
            )
            Files.createDirectories(directory)
            val outputPath = directory.resolve(file)
            OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8).use { writer ->
                writer.append(
                    content
                )
            }
        } catch (e: Exception) {
            error(e)
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
}