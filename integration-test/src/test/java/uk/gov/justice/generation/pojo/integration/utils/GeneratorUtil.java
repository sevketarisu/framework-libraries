package uk.gov.justice.generation.pojo.integration.utils;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import uk.gov.justice.generation.io.files.loader.SchemaLoader;
import uk.gov.justice.generation.pojo.core.GenerationContext;
import uk.gov.justice.generation.pojo.core.NameGenerator;
import uk.gov.justice.generation.pojo.generators.ClassGeneratable;
import uk.gov.justice.generation.pojo.generators.ClassNameFactory;
import uk.gov.justice.generation.pojo.generators.JavaGeneratorFactory;
import uk.gov.justice.generation.pojo.generators.TypeNameProvider;
import uk.gov.justice.generation.pojo.generators.plugin.DefaultPluginProvider;
import uk.gov.justice.generation.pojo.generators.plugin.PluginProvider;
import uk.gov.justice.generation.pojo.generators.plugin.TypeNamePluginProcessor;
import uk.gov.justice.generation.pojo.generators.plugin.classgenerator.PluginContext;
import uk.gov.justice.generation.pojo.visitable.Visitable;
import uk.gov.justice.generation.pojo.visitable.VisitableFactory;
import uk.gov.justice.generation.pojo.visitable.acceptor.AcceptorService;
import uk.gov.justice.generation.pojo.visitable.acceptor.DefaultAcceptorService;
import uk.gov.justice.generation.pojo.visitor.DefaultDefinitionFactory;
import uk.gov.justice.generation.pojo.visitor.DefinitionBuilderVisitor;
import uk.gov.justice.generation.pojo.visitor.DefinitionFactory;
import uk.gov.justice.generation.pojo.write.SourceWriter;

import java.io.File;
import java.util.List;

import org.everit.json.schema.Schema;
import org.json.JSONObject;

public class GeneratorUtil {

    private final NameGenerator nameGenerator = new NameGenerator();
    private final DefinitionFactory definitionFactory = new DefaultDefinitionFactory();
    private final DefinitionBuilderVisitor definitionBuilderVisitor = new DefinitionBuilderVisitor(definitionFactory);
    private final VisitableFactory visitableFactory = new VisitableFactory();
    private final SchemaLoader schemaLoader = new SchemaLoader();
    private final AcceptorService acceptorService = new DefaultAcceptorService(visitableFactory);

    public List<Class<?>> generateAndCompileJavaSource(final File jsonSchemaFile,
                                                       final String packageName,
                                                       final OutputDirectories outputDirectories) {
        return generateAndCompileJavaSource(
                jsonSchemaFile,
                packageName,
                outputDirectories,
                emptyList());
    }

    public List<Class<?>> generateAndCompileJavaSource(final File jsonSchemaFile,
                                                       final String packageName,
                                                       final OutputDirectories outputDirectories,
                                                       final List<String> ignoredClassNames) {

        final GenerationContext generationContext = new GenerationContext(
                outputDirectories.getSourceOutputDirectory(),
                packageName,
                jsonSchemaFile.getName(),
                ignoredClassNames);

        final Schema schema = schemaLoader.loadFrom(jsonSchemaFile);


        final String fieldName = nameGenerator.rootFieldNameFrom(jsonSchemaFile);

        final Visitable visitableSchema = visitableFactory.createWith(fieldName, schema, acceptorService);

        visitableSchema.accept(definitionBuilderVisitor);

        final SourceWriter sourceWriter = new SourceWriter();
        final ClassCompiler classCompiler = new ClassCompiler();
        final GeneratorFactoryBuilder generatorFactoryBuilder = new GeneratorFactoryBuilder();
        final PluginProvider pluginProvider = new DefaultPluginProvider();

        final TypeNameProvider typeNameProvider = new TypeNameProvider(generationContext);
        final TypeNamePluginProcessor typeNamePluginProcessor = new TypeNamePluginProcessor(pluginProvider);
        final ClassNameFactory classNameFactory = new ClassNameFactory(typeNameProvider, typeNamePluginProcessor);

        final JavaGeneratorFactory javaGeneratorFactory = generatorFactoryBuilder
                .withGenerationContext(generationContext)
                .withPluginProvider(pluginProvider)
                .withClassNameFactory(classNameFactory)
                .build();

        final PluginContext pluginContext = new PluginContext(javaGeneratorFactory, classNameFactory, generationContext.getSourceFilename());

        return javaGeneratorFactory
                .createClassGeneratorsFor(definitionBuilderVisitor.getDefinitions(), pluginProvider, pluginContext, generationContext)
                .stream()
                .map(classGeneratable -> writeAndCompile(
                        outputDirectories,
                        generationContext,
                        sourceWriter,
                        classCompiler,
                        classGeneratable))
                .collect(toList());
    }

    public void validate(final File schemaFile, final String json) {
        final Schema schema = schemaLoader.loadFrom(schemaFile);
        schema.validate(new JSONObject(json));
    }

    private Class<?> writeAndCompile(final OutputDirectories outputDirectories, final GenerationContext generationContext, final SourceWriter sourceWriter, final ClassCompiler classCompiler, final ClassGeneratable classGeneratable) {
        sourceWriter.write(classGeneratable, generationContext);
        return classCompiler.compile(classGeneratable, generationContext, outputDirectories.getClassesOutputDirectory().toFile());
    }
}
