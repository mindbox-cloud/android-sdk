package cloud.mindbox.mobile_sdk.processor

import cloud.mindbox.mobile_sdk.annotations.MindboxUrl
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@AutoService(Processor::class)
class MindboxUrlProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(MindboxUrl::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(MindboxUrl::class.java)
        if (annotatedElements.isEmpty()) {
            return false
        }

        val mapInitializer = CodeBlock.builder()
            .add("mapOf(\n")
            .indent()

        annotatedElements.forEachIndexed { index, element ->
            if (element.kind != ElementKind.CLASS) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Only classes can be annotated with @MindboxUrl",
                    element
                )
                return false
            }
            val className = (element as TypeElement).asClassName()
            val annotation = element.getAnnotation(MindboxUrl::class.java)
            val url = annotation.url

            mapInitializer.add("%S to %T::class.java", url, className)
            if (index < annotatedElements.size - 1) {
                mapInitializer.add(",\n")
            }
        }
        mapInitializer.add("\n")
            .unindent()
            .add(")")

        val activityClass = ClassName("android.app", "Activity")
        val mapType = ClassName("kotlin.collections", "Map")
            .parameterizedBy(
                ClassName("kotlin", "String"),
                ClassName("java.lang", "Class").parameterizedBy(
                    WildcardTypeName.producerOf(activityClass)
                )
            )

        val urlMapProperty = PropertySpec.builder("urlMap", mapType)
            .addAnnotation(JvmField::class)
            .initializer(mapInitializer.build())
            .build()

        val urlMapperObject = TypeSpec.objectBuilder("MindboxUrlMapper")
            .addProperty(urlMapProperty)
            .build()

        val file = FileSpec.builder("cloud.mindbox.mobile_sdk", "MindboxUrlMapper")
            .addType(urlMapperObject)
            .build()

        val kaptKotlinGeneratedDir = processingEnv.options["kapt.kotlin.generated"] ?: run {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Can't find the target directory for generated Kotlin files."
            )
            return false
        }
        file.writeTo(File(kaptKotlinGeneratedDir))

        return true
    }
}
