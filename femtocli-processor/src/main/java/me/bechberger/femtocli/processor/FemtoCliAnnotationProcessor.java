package me.bechberger.femtocli.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates reflection-free {@code CommandParser} implementations for every
 * {@code @Command}-annotated class.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("me.bechberger.femtocli.annotations.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class FemtoCliAnnotationProcessor extends AbstractProcessor {

    private static final String ANNO_PKG = "me.bechberger.femtocli.annotations";
    private static final String RT_PKG   = "me.bechberger.femtocli";

    private static final ClassName COMMAND_PARSER  = ClassName.get(RT_PKG, "CommandParser");
    private static final ClassName FEMTOCLI_RT     = ClassName.get(RT_PKG, "FemtoCliRuntime");
    private static final ClassName SPEC            = ClassName.get(RT_PKG, "Spec");
    private static final ClassName COMMAND_CONFIG  = ClassName.get(RT_PKG, "CommandConfig");
    private static final ClassName USAGE_EX        = ClassName.get(RT_PKG, "UsageEx");
    private static final ClassName TYPE_CONVERTER  = ClassName.get(RT_PKG, "TypeConverter");
    private static final ClassName VERIFIER_EX     = ClassName.get(RT_PKG, "VerifierException");
    private static final ClassName HELP_UTIL       = ClassName.get(RT_PKG, "HelpUtil");

    private static final String NO_DEFAULT = "__NO_DEFAULT_VALUE__";

    private static final ParameterizedTypeName DEQUE_STRING =
            ParameterizedTypeName.get(ClassName.get(Deque.class), ClassName.get(String.class));
    private static final ParameterizedTypeName MAP_CONVERTERS =
            ParameterizedTypeName.get(ClassName.get(Map.class),
                    ParameterizedTypeName.get(ClassName.get(Class.class),
                            WildcardTypeName.subtypeOf(Object.class)),
                    ParameterizedTypeName.get(TYPE_CONVERTER,
                            WildcardTypeName.subtypeOf(Object.class)));

    private Elements elements;
    private Types types;
    private Messager messager;
    /** The @Command-annotated class currently being processed (set in generateParser). */
    private TypeElement currentCommandType;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elements = env.getElementUtils();
        types    = env.getTypeUtils();
        messager = env.getMessager();
    }

    /* ================================================================
     *  Entry point
     * ================================================================ */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) return false;
        TypeElement commandAnno = elements.getTypeElement(ANNO_PKG + ".Command");
        if (commandAnno == null) return false;

        for (Element el : roundEnv.getElementsAnnotatedWith(commandAnno)) {
            if (el.getKind() != ElementKind.CLASS && el.getKind() != ElementKind.RECORD) continue;
            try {
                generateParser((TypeElement) el);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to generate parser: " + e.getMessage(), el);
            }
        }
        return true;
    }

    /* ================================================================
     *  Data models
     * ================================================================ */
    private record OptionInfo(
            VariableElement field,
            String[] names,
            String description,
            String paramLabel,
            boolean required,
            String defaultValue,
            String split,
            String arity,
            String converterClass,
            String converterMethod,
            boolean showDefaultValueInHelp,
            String defaultValueHelpTemplate,
            boolean defaultValueOnNewLine,
            String verifierClass,
            String verifierMethod,
            boolean hidden,
            boolean showEnumDescriptions,
            TypeMirror fieldType,
            String ownerField      // "" = direct, otherwise mixin field name
    ) {}

    private record ParamInfo(
            VariableElement field,
            String index,
            String arity,
            String description,
            String paramLabel,
            String defaultValue,
            String converterMethod,
            String converterClass,
            String verifierClass,
            String verifierMethod,
            TypeMirror fieldType,
            int[] indexRange,
            int[] arityRange
    ) {}

    private record MixinInfo(VariableElement field, TypeElement mixinType) {}
    private record SpecField(VariableElement field, String ownerField) {}
    private record SubcommandInfo(String name, String description, boolean hidden, String className) {}
    private record MethodSubcommandInfo(String name, String description, boolean hidden, ExecutableElement method) {}

    private record CommandMeta(
            String name,
            String[] description,
            String[] header,
            String[] customSynopsis,
            String version,
            String footer,
            boolean mixinStandardHelpOptions,
            boolean emptyLineAfterUsage,
            boolean emptyLineAfterDescription,
            String showDefaultValuesInHelp,
            boolean hidden,
            boolean agentMode
    ) {}

    /* ================================================================
     *  Annotation reading helpers
     * ================================================================ */
    private AnnotationMirror findAnnotation(Element el, String fqn) {
        for (AnnotationMirror am : el.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(fqn)) return am;
        }
        return null;
    }

    private String annoString(AnnotationMirror am, String name) {
        return annoValue(am, name, "");
    }

    @SuppressWarnings("unchecked")
    private <T> T annoValue(AnnotationMirror am, String name, T fallback) {
        if (am == null) return fallback;
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                try { return (T) e.getValue().getValue(); }
                catch (ClassCastException x) { return fallback; }
            }
        }
        return fallback;
    }

    private boolean annoBool(AnnotationMirror am, String name, boolean fallback) {
        if (am == null) return fallback;
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                Object v = e.getValue().getValue();
                if (v instanceof Boolean b) return b;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private String[] annoStringArr(AnnotationMirror am, String name) {
        if (am == null) return new String[0];
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                Object v = e.getValue().getValue();
                if (v instanceof List<?> list) {
                    return list.stream()
                            .map(av -> ((AnnotationValue) av).getValue().toString())
                            .toArray(String[]::new);
                }
            }
        }
        return new String[0];
    }

    private List<TypeMirror> annoClassArr(AnnotationMirror am, String name) {
        if (am == null) return List.of();
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                Object v = e.getValue().getValue();
                if (v instanceof List<?> list) {
                    return list.stream()
                            .map(av -> (TypeMirror) ((AnnotationValue) av).getValue())
                            .collect(Collectors.toList());
                }
            }
        }
        return List.of();
    }

    private String annoClassName(AnnotationMirror am, String name) {
        if (am == null) return "";
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                Object v = e.getValue().getValue();
                if (v instanceof TypeMirror tm) return tm.toString();
            }
        }
        return "";
    }

    private String annoEnumName(AnnotationMirror am, String name) {
        if (am == null) return "";
        for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (e.getKey().getSimpleName().toString().equals(name)) {
                Object v = e.getValue().getValue();
                if (v instanceof VariableElement ve) return ve.getSimpleName().toString();
            }
        }
        return "";
    }

    /* ================================================================
     *  Model collection from TypeElement
     * ================================================================ */
    private CommandMeta readCommandMeta(TypeElement type) {
        AnnotationMirror cmd = findAnnotation(type, ANNO_PKG + ".Command");
        if (cmd == null) return null;
        return new CommandMeta(
                annoString(cmd, "name"),
                annoStringArr(cmd, "description"),
                annoStringArr(cmd, "header"),
                annoStringArr(cmd, "customSynopsis"),
                annoString(cmd, "version"),
                annoString(cmd, "footer"),
                annoBool(cmd, "mixinStandardHelpOptions", false),
                annoBool(cmd, "emptyLineAfterUsage", false),
                annoBool(cmd, "emptyLineAfterDescription", false),
                annoEnumName(cmd, "showDefaultValuesInHelp"),
                annoBool(cmd, "hidden", false),
                annoBool(cmd, "agentMode", false)
        );
    }

    /** Collect all fields (own + inherited), base-first. */
    private List<VariableElement> allFields(TypeElement type) {
        List<VariableElement> result = new ArrayList<>();
        collectFields(type, result, new HashSet<>());
        return result;
    }

    private void collectFields(TypeElement type, List<VariableElement> out, Set<String> visited) {
        if (type == null) return;
        String qn = type.getQualifiedName().toString();
        if ("java.lang.Object".equals(qn) || !visited.add(qn)) return;
        TypeMirror sup = type.getSuperclass();
        if (sup.getKind() != TypeKind.NONE) {
            Element se = types.asElement(sup);
            if (se instanceof TypeElement st) collectFields(st, out, visited);
        }
        out.addAll(ElementFilter.fieldsIn(type.getEnclosedElements()));
    }

    private List<OptionInfo> collectOptions(TypeElement type, String ownerField) {
        List<OptionInfo> opts = new ArrayList<>();
        AnnotationMirror ignore = findAnnotation(type, ANNO_PKG + ".IgnoreOptions");
        boolean ignoreAll = annoBool(ignore, "ignoreAll", false);
        String[] excludes = annoStringArr(ignore, "exclude");
        String[] includes = annoStringArr(ignore, "include");
        String[] depOpts  = annoStringArr(ignore, "options");

        for (VariableElement f : allFields(type)) {
            AnnotationMirror oa = findAnnotation(f, ANNO_PKG + ".Option");
            if (oa == null) continue;
            if (f.getModifiers().contains(Modifier.FINAL)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Option field must not be final", f);
                continue;
            }
            if (f.getModifiers().contains(Modifier.PRIVATE)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Option field must not be private (use package-private or higher)", f);
                continue;
            }
            String[] names = annoStringArr(oa, "names");
            if (!shouldInclude(ignoreAll, excludes, includes, depOpts, f, names)) continue;
            opts.add(new OptionInfo(f, names,
                    annoString(oa, "description"),
                    annoString(oa, "paramLabel"),
                    annoBool(oa, "required", false),
                    annoString(oa, "defaultValue"),
                    annoString(oa, "split"),
                    annoString(oa, "arity"),
                    annoClassName(oa, "converter"),
                    annoString(oa, "converterMethod"),
                    annoBool(oa, "showDefaultValueInHelp", true),
                    annoString(oa, "defaultValueHelpTemplate"),
                    annoBool(oa, "defaultValueOnNewLine", false),
                    annoClassName(oa, "verifier"),
                    annoString(oa, "verifierMethod"),
                    annoBool(oa, "hidden", false),
                    annoBool(oa, "showEnumDescriptions", false),
                    f.asType(), ownerField));
        }
        return opts;
    }

    private boolean shouldInclude(boolean ignoreAll, String[] excludes, String[] includes,
                                  String[] depOpts, VariableElement field, String[] names) {
        if (ignoreAll) return matchesAny(includes, field, names);
        if (matchesAny(includes, field, names)) return true;
        if (matchesAny(excludes, field, names)) return false;
        return !matchesAny(depOpts, field, names);
    }

    private boolean matchesAny(String[] rules, VariableElement field, String[] optNames) {
        if (rules == null || rules.length == 0) return false;
        String fr = "field:" + field.getSimpleName();
        for (String r : rules) {
            if (r.equals(fr)) return true;
            for (String n : optNames) if (n.equals(r)) return true;
        }
        return false;
    }

    private List<ParamInfo> collectParams(TypeElement type) {
        List<ParamInfo> params = new ArrayList<>();
        for (VariableElement f : allFields(type)) {
            AnnotationMirror pa = findAnnotation(f, ANNO_PKG + ".Parameters");
            if (pa == null) continue;
            if (f.getModifiers().contains(Modifier.FINAL)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Parameters field must not be final", f);
                continue;
            }
            if (f.getModifiers().contains(Modifier.PRIVATE)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Parameters field must not be private (use package-private or higher)", f);
                continue;
            }
            String idx = annoString(pa, "index");
            String ar  = annoString(pa, "arity");
            if (idx.isEmpty()) idx = "0";
            params.add(new ParamInfo(f, idx, ar,
                    annoString(pa, "description"),
                    annoString(pa, "paramLabel"),
                    annoString(pa, "defaultValue"),
                    annoString(pa, "converterMethod"),
                    annoClassName(pa, "converter"),
                    annoClassName(pa, "verifier"),
                    annoString(pa, "verifierMethod"),
                    f.asType(),
                    parseRange(idx), parseRange(ar)));
        }
        params.sort(Comparator.comparingInt(p -> p.indexRange[0]));
        return params;
    }

    private List<MixinInfo> collectMixins(TypeElement type) {
        List<MixinInfo> mixins = new ArrayList<>();
        for (VariableElement f : allFields(type)) {
            AnnotationMirror ma = findAnnotation(f, ANNO_PKG + ".Mixin");
            if (ma == null) continue;
            if (f.getModifiers().contains(Modifier.STATIC)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Mixin field must not be static", f);
                continue;
            }
            Element te = types.asElement(f.asType());
            if (te instanceof TypeElement mixinType) mixins.add(new MixinInfo(f, mixinType));
        }
        return mixins;
    }

    private List<SpecField> collectSpecFields(TypeElement type, String ownerField) {
        List<SpecField> specs = new ArrayList<>();
        TypeElement specType = elements.getTypeElement(RT_PKG + ".Spec");
        if (specType == null) return specs;
        for (VariableElement f : allFields(type)) {
            if (types.isSameType(f.asType(), specType.asType())) {
                specs.add(new SpecField(f, ownerField));
            }
        }
        return specs;
    }

    private List<SubcommandInfo> collectSubcommands(TypeElement type) {
        AnnotationMirror cmd = findAnnotation(type, ANNO_PKG + ".Command");
        List<SubcommandInfo> subs = new ArrayList<>();
        for (TypeMirror tm : annoClassArr(cmd, "subcommands")) {
            Element el = types.asElement(tm);
            if (el instanceof TypeElement te) {
                AnnotationMirror sc = findAnnotation(te, ANNO_PKG + ".Command");
                if (sc != null) {
                    subs.add(new SubcommandInfo(
                            annoString(sc, "name"),
                            firstOf(annoStringArr(sc, "description")),
                            annoBool(sc, "hidden", false),
                            te.getQualifiedName().toString()));
                }
            }
        }
        return subs;
    }

    private List<MethodSubcommandInfo> collectMethodSubcommands(TypeElement type) {
        List<MethodSubcommandInfo> subs = new ArrayList<>();
        for (ExecutableElement m : ElementFilter.methodsIn(type.getEnclosedElements())) {
            AnnotationMirror cmd = findAnnotation(m, ANNO_PKG + ".Command");
            if (cmd == null) continue;
            subs.add(new MethodSubcommandInfo(
                    annoString(cmd, "name"),
                    firstOf(annoStringArr(cmd, "description")),
                    annoBool(cmd, "hidden", false), m));
        }
        return subs;
    }

    /* ================================================================
     *  Top-level code generation
     * ================================================================ */
    private void generateParser(TypeElement typeElement) throws IOException {
        currentCommandType = typeElement;
        String binaryName = elements.getBinaryName(typeElement).toString();
        int lastDot = binaryName.lastIndexOf('.');
        String pkg = lastDot >= 0 ? binaryName.substring(0, lastDot) : "";
        String parserName = binaryName.substring(lastDot + 1) + "CommandParser";
        ClassName cmdCN = ClassName.get(typeElement);

        CommandMeta meta = readCommandMeta(typeElement);
        if (meta == null) return;

        List<MixinInfo> mixins = collectMixins(typeElement);
        List<SpecField> specFields = new ArrayList<>(collectSpecFields(typeElement, ""));
        for (MixinInfo mi : mixins) {
            specFields.addAll(collectSpecFields(mi.mixinType, mi.field.getSimpleName().toString()));
        }

        List<OptionInfo> allOptions = new ArrayList<>();
        for (MixinInfo mi : mixins)
            allOptions.addAll(collectOptions(mi.mixinType, mi.field.getSimpleName().toString()));
        allOptions.addAll(collectOptions(typeElement, ""));

        List<ParamInfo> params = collectParams(typeElement);
        List<SubcommandInfo> subcommands = collectSubcommands(typeElement);
        List<MethodSubcommandInfo> methodSubs = collectMethodSubcommands(typeElement);

        Set<String> allOptNames = new LinkedHashSet<>();
        for (OptionInfo o : allOptions) Collections.addAll(allOptNames, o.names);

        ParameterizedTypeName parserType = ParameterizedTypeName.get(COMMAND_PARSER, cmdCN);

        TypeSpec.Builder cls = TypeSpec.classBuilder(parserName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(parserType)
                .addJavadoc("Generated parser for {@link $T}.\n", cmdCN);

        // static OPTION_NAMES field
        if (!allOptNames.isEmpty()) {
            cls.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(Set.class, String.class),
                            "OPTION_NAMES", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of($L)", Set.class,
                            allOptNames.stream().map(n -> "\"" + n + "\"")
                                    .collect(Collectors.joining(", ")))
                    .build());
        }

        cls.addMethod(buildName(meta));
        cls.addMethod(buildVersion(meta));
        cls.addMethod(buildInit(cmdCN, mixins, specFields));
        cls.addMethod(buildRenderHelp(meta, allOptions, params, subcommands, methodSubs));
        cls.addMethod(buildParse(cmdCN, allOptions, params, allOptNames, mixins));

        if (!subcommands.isEmpty() || !methodSubs.isEmpty()) {
            cls.addMethod(buildResolveSubcommand(subcommands, methodSubs));
            cls.addMethod(buildCreateSubcommand(subcommands, methodSubs, cmdCN));
            cls.addMethod(buildSubcommandParser(subcommands));
        }

        if (!methodSubs.isEmpty()) {
            cls.addMethod(buildInvokeMethodSubcommand(cmdCN, methodSubs));
        }

        if (meta.agentMode && !allOptions.isEmpty()) {
            cls.addMethod(buildNormalizeAgent(allOptions));
        }

        if (meta.agentMode) {
            cls.addMethod(MethodSpec.methodBuilder("supportsAgentMode")
                    .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class).addStatement("return true").build());
        }

        JavaFile.builder(pkg, cls.build())
                .addFileComment("Generated by femtocli annotation processor – DO NOT EDIT")
                .build()
                .writeTo(processingEnv.getFiler());
    }

    /* ================================================================
     *  name() / version()
     * ================================================================ */
    private MethodSpec buildName(CommandMeta meta) {
        return MethodSpec.methodBuilder("name")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(String.class).addStatement("return $S", meta.name).build();
    }
    private MethodSpec buildVersion(CommandMeta meta) {
        return MethodSpec.methodBuilder("version")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(String.class).addStatement("return $S", meta.version).build();
    }

    /* ================================================================
     *  init()
     * ================================================================ */
    private MethodSpec buildInit(ClassName cmdCN, List<MixinInfo> mixins, List<SpecField> specFields) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("init")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(cmdCN, "cmd")
                .addParameter(ClassName.get("java.io", "PrintStream"), "out")
                .addParameter(ClassName.get("java.io", "PrintStream"), "err")
                .addParameter(ParameterizedTypeName.get(List.class, String.class), "commandPath")
                .addParameter(COMMAND_CONFIG, "config");
        for (MixinInfo mi : mixins) {
            String fn = mi.field.getSimpleName().toString();
            m.addStatement("cmd.$L = new $T()", fn, ClassName.get(mi.mixinType));
        }
        for (SpecField sf : specFields) {
            String fn = sf.field.getSimpleName().toString();
            String owner = sf.ownerField.isEmpty() ? "cmd" : "cmd." + sf.ownerField;
            m.addStatement("$L.$L = new $T($L, out, err, commandPath, config)",
                    owner, fn, SPEC, owner);
        }
        return m.build();
    }

    /* ================================================================
     *  renderHelp() — generated method that renders help text directly
     * ================================================================ */
    private MethodSpec buildRenderHelp(CommandMeta meta, List<OptionInfo> options,
                                       List<ParamInfo> params,
                                       List<SubcommandInfo> subCmds,
                                       List<MethodSubcommandInfo> methodSubs) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("renderHelp")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("java.io", "PrintStream"), "out")
                .addParameter(String.class, "displayPath")
                .addParameter(COMMAND_CONFIG, "config")
                .addParameter(boolean.class, "agentMode");

        boolean hasSubcommands = !subCmds.isEmpty() || !methodSubs.isEmpty();
        boolean annoMixin = meta.mixinStandardHelpOptions;

        // Determine showStandardHelp at runtime (config || annotation)
        if (annoMixin) {
            m.addStatement("boolean showStdHelp = true");
        } else {
            m.addStatement("boolean showStdHelp = config.mixinStandardHelpOptions");
        }

        // ---- Header ----
        for (String h : meta.header) {
            m.addStatement("out.println($S)", h);
        }

        // ---- Synopsis ----
        List<OptionInfo> visibleOptions = options.stream().filter(o -> !o.hidden).toList();

        if (meta.customSynopsis.length > 0) {
            // Custom synopsis — emit as literal lines
            for (String line : meta.customSynopsis) {
                m.addStatement("out.println($S)", line);
            }
        } else {
            // Auto-generated synopsis
            m.addStatement("$T synBuf = new $T()", StringBuilder.class, StringBuilder.class);
            m.addStatement("synBuf.append($S).append(displayPath)", "Usage: ");

            m.beginControlFlow("if (agentMode)");
            m.beginControlFlow("if (showStdHelp)");
            m.addStatement("synBuf.append($S)", ",[hV]");
            m.endControlFlow();
            for (OptionInfo o : visibleOptions) {
                String optName = stripLeadingDashes(last(o.names));
                String label = o.paramLabel.isEmpty()
                        ? "<" + o.field.getSimpleName() + ">" : o.paramLabel;
                if (!isBooleanType(o.fieldType)) {
                    String token = optName + "=" + label;
                    if (o.required) {
                        m.addStatement("synBuf.append($S)", "," + token);
                    } else {
                        m.addStatement("synBuf.append($S)", ",[" + token + "]");
                    }
                } else if (!o.required) {
                    m.addStatement("synBuf.append($S)", ",[" + optName + "]");
                }
            }
            if (hasSubcommands) m.addStatement("synBuf.append($S)", ",[COMMAND]");
            for (ParamInfo p : params) {
                String label = paramLabel(p);
                m.addStatement("synBuf.append($S)", "," + label);
            }
            m.addStatement("out.println(synBuf)");

            m.nextControlFlow("else");
            m.beginControlFlow("if (showStdHelp)");
            m.addStatement("synBuf.append($S)", " [-hV]");
            m.endControlFlow();
            for (OptionInfo o : visibleOptions) {
                String optName = last(o.names);
                String label = o.paramLabel.isEmpty()
                        ? "<" + o.field.getSimpleName() + ">" : o.paramLabel;
                if (!isBooleanType(o.fieldType)) {
                    if (o.required) {
                        m.addStatement("synBuf.append($S)", " " + optName + "=" + label);
                    } else {
                        m.addStatement("synBuf.append($S)", " [" + optName + "=" + label + "]");
                    }
                } else if (!o.required) {
                    m.addStatement("synBuf.append($S)", " [" + optName + "]");
                }
            }
            if (hasSubcommands) m.addStatement("synBuf.append($S)", " [COMMAND]");
            for (ParamInfo p : params) {
                String label = paramLabel(p);
                m.addStatement("synBuf.append($S)", " " + label);
            }
            m.addStatement("String synStr = synBuf.toString()");
            m.addStatement("int synIndent = $S.length() + displayPath.length() + 1", "Usage: ");
            m.addStatement("out.println($T.wrapBlock(synStr, 80, synIndent))", HELP_UTIL);
            m.endControlFlow();
        }

        // ---- Empty line after usage ----
        if (meta.emptyLineAfterUsage) {
            m.addStatement("out.println()");
        } else {
            m.beginControlFlow("if (config.emptyLineAfterUsage)");
            m.addStatement("out.println()");
            m.endControlFlow();
        }

        // ---- Description ----
        if (meta.description.length > 0) {
            m.beginControlFlow("if (!agentMode)");
            m.addStatement("out.println($S)", meta.description[0]);
            m.endControlFlow();
        }

        // ---- Empty line after description ----
        if (meta.emptyLineAfterDescription) {
            m.addStatement("out.println()");
        } else {
            m.beginControlFlow("if (config.emptyLineAfterDescription)");
            m.addStatement("out.println()");
            m.endControlFlow();
        }

        // ---- Determine show defaults (compile-time where possible) ----
        switch (meta.showDefaultValuesInHelp) {
            case "ENABLE":
                m.addStatement("boolean showDefaults = true");
                break;
            case "DISABLE":
                m.addStatement("boolean showDefaults = false");
                break;
            default: // INHERIT
                m.addStatement("boolean showDefaults = config.showDefaultValuesInHelp");
                break;
        }

        // ---- Build option entries (compile-time data) ----
        record HelpEntry(String normalLabel, String agentLabel,
                         String description, boolean hasShort, boolean isOption,
                         String defaultValue, String defaultValueHelpTemplate,
                         boolean defaultValueOnNewLine, boolean showDefaultInHelp,
                         boolean required) {}

        List<HelpEntry> paramEntries = new ArrayList<>();
        for (ParamInfo p : params) {
            String label = paramLabel(p);
            paramEntries.add(new HelpEntry(label, label, p.description, false, false,
                    null, null, false, false, false));
        }

        List<HelpEntry> optionEntries = new ArrayList<>();
        for (OptionInfo o : visibleOptions) {
            String[] sortedNames = o.names.clone();
            java.util.Arrays.sort(sortedNames, Comparator.comparingInt(String::length));

            // Normal mode label (e.g., "-V, --version" or "--verbose=<val>")
            StringBuilder normalLabel = new StringBuilder();
            normalLabel.append(String.join(", ", sortedNames));
            if (!isBooleanType(o.fieldType)) {
                String pl = o.paramLabel.isEmpty() ? "<" + o.field.getSimpleName() + ">" : o.paramLabel;
                normalLabel.append("=").append(pl);
            }

            // Agent mode label (e.g., "V, version" or "verbose=<val>")
            StringBuilder agentLabel = new StringBuilder();
            for (int i = 0; i < sortedNames.length; i++) {
                if (i > 0) agentLabel.append(", ");
                agentLabel.append(stripLeadingDashes(sortedNames[i]));
            }
            if (!isBooleanType(o.fieldType)) {
                String pl = o.paramLabel.isEmpty() ? "<" + o.field.getSimpleName() + ">" : o.paramLabel;
                agentLabel.append("=").append(pl);
            }

            String desc = expandPlaceholders(o);
            boolean showDef = o.showDefaultValueInHelp && !o.description.contains("${DEFAULT-VALUE}");

            optionEntries.add(new HelpEntry(
                    normalLabel.toString(), agentLabel.toString(),
                    desc, hasShort(o.names), true,
                    o.defaultValue, o.defaultValueHelpTemplate,
                    o.defaultValueOnNewLine, showDef, o.required));
        }

        // ---- Agent mode rendering ----
        m.beginControlFlow("if (agentMode)");
        {
            // Compute label width at compile-time (including std help labels)
            int agentMaxLblWithStd = Math.max("h, help".length(), "V, version".length());
            int agentMaxLblNoStd = 0;
            for (HelpEntry e : paramEntries) {
                agentMaxLblWithStd = Math.max(agentMaxLblWithStd, e.agentLabel.length());
                agentMaxLblNoStd = Math.max(agentMaxLblNoStd, e.agentLabel.length());
            }
            for (HelpEntry e : optionEntries) {
                agentMaxLblWithStd = Math.max(agentMaxLblWithStd, e.agentLabel.length());
                agentMaxLblNoStd = Math.max(agentMaxLblNoStd, e.agentLabel.length());
            }
            agentMaxLblWithStd = Math.max(12, agentMaxLblWithStd);
            agentMaxLblNoStd = Math.max(12, agentMaxLblNoStd);

            m.addStatement("out.println($S)", "Options:");
            m.addStatement("$T<String[]> helpEntries = new $T<>()", List.class, ArrayList.class);
            m.addStatement("int descCol");

            m.beginControlFlow("if (showStdHelp)");
            m.addStatement("descCol = $L + 6", agentMaxLblWithStd);
            // Sort key for standard help uses same convention as old code:
            // label.replaceFirst("^\\s*-+", "").toLowerCase()
            m.addStatement("helpEntries.add(new String[]{$S, $S, $S})",
                    "  h, help", "Show this help message and exit.",
                    "h, help");
            m.addStatement("helpEntries.add(new String[]{$S, $S, $S})",
                    "  V, version", "Print version information and exit.",
                    "v, version");
            m.nextControlFlow("else");
            m.addStatement("descCol = $L + 6", agentMaxLblNoStd);
            m.endControlFlow();

            // Add param entries (no sorting — they go first)
            for (HelpEntry e : paramEntries) {
                m.addStatement("helpEntries.add(new String[]{$S, $S, $S})",
                        "      " + e.agentLabel, e.description,
                        e.agentLabel.replaceFirst("^\\s*-+", "").toLowerCase());
            }

            // Add option entries
            for (HelpEntry e : optionEntries) {
                String prefix = e.hasShort ? "  " : "      ";
                String sortKey = e.agentLabel.replaceFirst("^\\s*-+", "").toLowerCase();
                if (e.showDefaultInHelp && !NO_DEFAULT.equals(e.defaultValue)) {
                    m.addStatement("{ String __desc = $S", e.description);
                    emitDefaultValueAppend(m, e.defaultValue, e.defaultValueHelpTemplate,
                            e.defaultValueOnNewLine, e.required);
                    m.addStatement("helpEntries.add(new String[]{$S, __desc, $S})",
                            prefix + e.agentLabel, sortKey);
                    m.addStatement("}");
                } else {
                    String desc = e.description;
                    if (e.required) desc += " (required)";
                    m.addStatement("helpEntries.add(new String[]{$S, $S, $S})",
                            prefix + e.agentLabel, desc, sortKey);
                }
            }

            m.addStatement("helpEntries.sort($T.comparing(e -> e[2]))", Comparator.class);
            m.beginControlFlow("for (String[] entry : helpEntries)");
            m.addStatement("$T.printAligned(out, entry[0], entry[1], descCol, 80)", HELP_UTIL);
            m.endControlFlow();
        }

        m.nextControlFlow("else"); // !agentMode — normal mode
        {
            // Compute label width (compile-time, including standard help options)
            int normalMaxLbl = 0;
            for (HelpEntry e : paramEntries) {
                normalMaxLbl = Math.max(normalMaxLbl, e.normalLabel.length());
            }
            // Always count standard help labels in width (if showStdHelp is false at runtime,
            // descCol might be slightly wider than necessary — cosmetically acceptable)
            normalMaxLbl = Math.max(normalMaxLbl, "-h, --help".length());
            normalMaxLbl = Math.max(normalMaxLbl, "-V, --version".length());
            for (HelpEntry e : optionEntries) {
                normalMaxLbl = Math.max(normalMaxLbl, e.normalLabel.length());
            }
            normalMaxLbl = Math.max(12, normalMaxLbl);

            m.addStatement("int descCol = $L + 6", normalMaxLbl);

            // Print parameter entries (unsorted, before options)
            for (HelpEntry e : paramEntries) {
                m.addStatement("$T.printAligned(out, $S, $S, descCol, 80)",
                        HELP_UTIL, "      " + e.normalLabel, e.description);
            }

            // Option entries (sorted)
            m.addStatement("$T<String[]> optEntries = new $T<>()", List.class, ArrayList.class);
            m.beginControlFlow("if (showStdHelp)");
            // Sort keys match old renderer: label.replaceFirst("^\\s*-+","").toLowerCase()
            m.addStatement("optEntries.add(new String[]{$S, $S, $S})",
                    "  -h, --help", "Show this help message and exit.",
                    "h, --help");
            m.addStatement("optEntries.add(new String[]{$S, $S, $S})",
                    "  -V, --version", "Print version information and exit.",
                    "v, --version");
            m.endControlFlow();

            for (HelpEntry e : optionEntries) {
                String prefix = e.hasShort ? "  " : "      ";
                String sortKey = e.normalLabel.replaceFirst("^\\s*-+", "").toLowerCase();
                if (e.showDefaultInHelp && !NO_DEFAULT.equals(e.defaultValue)) {
                    m.addStatement("{ String __desc = $S", e.description);
                    emitDefaultValueAppend(m, e.defaultValue, e.defaultValueHelpTemplate,
                            e.defaultValueOnNewLine, e.required);
                    m.addStatement("optEntries.add(new String[]{$S, __desc, $S})",
                            prefix + e.normalLabel, sortKey);
                    m.addStatement("}");
                } else {
                    String desc = e.description;
                    if (e.required) desc += " (required)";
                    m.addStatement("optEntries.add(new String[]{$S, $S, $S})",
                            prefix + e.normalLabel, desc, sortKey);
                }
            }
            m.addStatement("optEntries.sort($T.comparing(e -> e[2]))", Comparator.class);
            m.beginControlFlow("for (String[] entry : optEntries)");
            m.addStatement("$T.printAligned(out, entry[0], entry[1], descCol, 80)", HELP_UTIL);
            m.endControlFlow();
        }
        m.endControlFlow(); // agentMode if/else

        // ---- Subcommands ----
        List<Object[]> subEntries = new ArrayList<>();
        for (SubcommandInfo s : subCmds)
            if (!s.hidden) subEntries.add(new Object[]{s.name, s.description});
        for (MethodSubcommandInfo ms : methodSubs)
            if (!ms.hidden) subEntries.add(new Object[]{ms.name, ms.description});

        if (!subEntries.isEmpty()) {
            m.addStatement("out.println($S)", "Commands:");
            int maxSubLen = subEntries.stream().mapToInt(e -> ((String) e[0]).length()).max().orElse(0);
            for (Object[] e : subEntries) {
                m.addStatement("out.printf($S, $S, $S)", "  %-" + (maxSubLen + 2) + "s%s%n", e[0], e[1]);
            }
        }

        // ---- Footer ----
        if (meta.footer != null && !meta.footer.isBlank()) {
            m.addStatement("out.println()");
            if (meta.footer.endsWith("\n") || meta.footer.endsWith("\r")) {
                m.addStatement("out.print($S)", meta.footer);
            } else {
                m.addStatement("out.println($S)", meta.footer);
            }
        }

        return m.build();
    }

    /** Emit code that conditionally appends default value to __desc variable. */
    private void emitDefaultValueAppend(MethodSpec.Builder m, String defaultValue,
                                         String defaultValueHelpTemplate,
                                         boolean defaultValueOnNewLine, boolean required) {
        m.beginControlFlow("if (showDefaults)");
        if (defaultValueHelpTemplate != null && !defaultValueHelpTemplate.isEmpty()) {
            m.addStatement("String __tpl = $S", defaultValueHelpTemplate);
        } else {
            m.addStatement("String __tpl = config.effectiveDefaultValueHelpTemplate()");
        }
        m.addStatement("String __rendered = __tpl.replace($S, $S)", "${DEFAULT-VALUE}", defaultValue);
        if (defaultValueOnNewLine) {
            m.addStatement("__desc = __desc.isEmpty() ? __rendered.stripLeading() : __desc + $S + __rendered.stripLeading()", "\n");
        } else {
            m.beginControlFlow("if (config.effectiveDefaultValueOnNewLine())");
            m.addStatement("__desc = __desc.isEmpty() ? __rendered.stripLeading() : __desc + $S + __rendered.stripLeading()", "\n");
            m.nextControlFlow("else");
            m.addStatement("__desc = __desc.isEmpty() ? __rendered.stripLeading() : __desc + __rendered");
            m.endControlFlow();
        }
        m.endControlFlow();
        if (required) m.addStatement("__desc += $S", " (required)");
    }

    /* ================================================================
     *  parse()  — the big one
     * ================================================================ */
    private MethodSpec buildParse(ClassName cmdCN,
                                  List<OptionInfo> allOptions,
                                  List<ParamInfo> params,
                                  Set<String> allOptNames,
                                  List<MixinInfo> mixins) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("parse")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(cmdCN, "cmd")
                .addParameter(DEQUE_STRING, "tokens")
                .addParameter(MAP_CONVERTERS, "converters")
                .addParameter(COMMAND_CONFIG, "config")
                .addException(Exception.class);

        m.addStatement("$T<String> seen = new $T<>()", Set.class, HashSet.class);
        m.addStatement("$T<String> positionals = new $T<>()", List.class, ArrayList.class);
        m.addStatement("boolean acceptOpts = true");

        // Multi-value accumulators
        Map<String, OptionInfo> multiOpts = new LinkedHashMap<>();
        for (OptionInfo o : allOptions) {
            if (isArray(o.fieldType) || isList(o.fieldType)) {
                String fn = o.field.getSimpleName().toString();
                multiOpts.put(fn, o);
                m.addStatement("$T<String> __m_$L = new $T<>()", List.class, fn, ArrayList.class);
            }
        }

        // Main loop
        m.beginControlFlow("while (!tokens.isEmpty())");
        m.addStatement("String tok = tokens.removeFirst()");
        m.addStatement("$T.throwIfHelpOrVersion(cmd, tok)", FEMTOCLI_RT);

        // "--" separator
        m.beginControlFlow("if (acceptOpts && $S.equals(tok))", "--");
        m.addStatement("acceptOpts = false").addStatement("continue");
        m.endControlFlow();

        // Token starts with '-'
        m.beginControlFlow("if (acceptOpts && tok.startsWith($S))", "-");
        m.addStatement("int eq = tok.indexOf('=')");
        m.addStatement("String name = eq >= 0 ? tok.substring(0, eq) : tok");
        m.addStatement("String val = eq >= 0 ? tok.substring(eq + 1) : null");

        // Switch on option names
        boolean first = true;
        for (OptionInfo o : allOptions) {
            StringBuilder cond = new StringBuilder();
            for (int i = 0; i < o.names.length; i++) {
                if (i > 0) cond.append(" || ");
                cond.append("\"").append(o.names[i]).append("\".equals(name)");
            }
            if (first) { m.beginControlFlow("if ($L)", cond); first = false; }
            else       { m.nextControlFlow("else if ($L)", cond); }

            m.addStatement("seen.add($S)", o.names[0]);
            String fn = o.field.getSimpleName().toString();
            boolean isBool = isBooleanType(o.fieldType);
            boolean isMulti = isArray(o.fieldType) || isList(o.fieldType);

            if (isBool && !hasCustomConverter(o)) {
                // Boolean flag
                m.beginControlFlow("if (val == null)");
                m.beginControlFlow("if (!tokens.isEmpty() && $T.looksLikeExplicitBooleanValue(tokens.peekFirst()))",
                        FEMTOCLI_RT);
                m.addStatement("val = tokens.removeFirst()");
                m.nextControlFlow("else");
                emitFieldSet(m, o, "true");
                m.addStatement("continue");
                m.endControlFlow();
                m.endControlFlow();
            } else if ("0..1".equals(o.arity)) {
                m.beginControlFlow("if (val == null)");
                // Arity 0..1: flag given without value → use defaultValue if present
                if (!NO_DEFAULT.equals(o.defaultValue)) {
                    emitConvertAndSet(m, o, "\"" + escJava(o.defaultValue) + "\"", false);
                }
                m.addStatement("continue");
                m.endControlFlow();
            } else {
                // Require value
                m.beginControlFlow("if (val == null)");
                m.beginControlFlow("if (tokens.isEmpty())");
                m.addStatement("throw $T.usageError(cmd, $S + name)", FEMTOCLI_RT,
                        "Missing value for option: ");
                m.endControlFlow();
                m.addStatement("val = tokens.removeFirst()");
                m.endControlFlow();
            }

            if (isMulti) {
                if (!o.split.isEmpty()) {
                    m.addStatement("for (String __sv : val.split($S)) __m_$L.add(__sv)",
                            o.split, fn);
                } else {
                    m.addStatement("__m_$L.add(val)", fn);
                }
            } else {
                emitConvertAndSet(m, o, "val", true);
            }
        }

        // Unknown option
        if (!first) m.nextControlFlow("else");
        else        m.beginControlFlow("");
        if (!allOptNames.isEmpty()) {
            m.addStatement("throw $T.usageError(cmd, $T.unknownOptionMessage(name, OPTION_NAMES, config))",
                    FEMTOCLI_RT, FEMTOCLI_RT);
        } else {
            m.addStatement("throw $T.usageError(cmd, $S + name)", FEMTOCLI_RT, "Unknown option: ");
        }
        m.endControlFlow(); // if/else chain

        m.nextControlFlow("else"); // not an option
        m.addStatement("positionals.add(tok)");
        m.endControlFlow(); // if starts with '-'
        m.endControlFlow(); // while

        // Apply multi-value options
        for (var entry : multiOpts.entrySet()) {
            String fn = entry.getKey();
            OptionInfo o = entry.getValue();
            String owner = o.ownerField.isEmpty() ? "cmd" : "cmd." + o.ownerField;
            if (isArray(o.fieldType)) {
                String ct = arrayComponentName(o.fieldType);
                m.beginControlFlow("if (!__m_$L.isEmpty())", fn);
                m.addStatement("$L[] __arr = new $L[__m_$L.size()]", ct, ct, fn);
                m.beginControlFlow("for (int __i = 0; __i < __m_$L.size(); __i++)", fn);
                m.addStatement("__arr[__i] = ($L) $T.convert(__m_$L.get(__i), $L.class, converters)",
                        boxed(ct), FEMTOCLI_RT, fn, ct);
                m.endControlFlow();
                m.addStatement("$L.$L = __arr", owner, fn);
                m.endControlFlow();
            } else { // List
                String elemType = listElementTypeName(o.fieldType);
                if ("java.lang.String".equals(elemType)) {
                    m.addStatement("$L.$L = new $T<>(__m_$L)", owner, fn, ArrayList.class, fn);
                } else {
                    m.addStatement("$L.$L = new $T<>()", owner, fn, ArrayList.class);
                    m.beginControlFlow("for (String __s : __m_$L)", fn);
                    m.addStatement("$L.$L.add(($L) $T.convert(__s, $L.class, converters))",
                            owner, fn, elemType, FEMTOCLI_RT, elemType);
                    m.endControlFlow();
                }
            }
        }

        // Defaults for unseen options
        for (OptionInfo o : allOptions) {
            if (NO_DEFAULT.equals(o.defaultValue)) continue;
            String owner = o.ownerField.isEmpty() ? "cmd" : "cmd." + o.ownerField;
            m.beginControlFlow("if (!seen.contains($S))", o.names[0]);
            emitConvertAndSetOwner(m, o, "\"" + escJava(o.defaultValue) + "\"", owner, false);
            m.endControlFlow();
        }

        // Required options
        for (OptionInfo o : allOptions) {
            if (!o.required) continue;
            String pref = preferredLong(o.names);
            m.beginControlFlow("if (!seen.contains($S))", o.names[0]);
            m.addStatement("throw $T.usageError(cmd, $S)", FEMTOCLI_RT,
                    "Missing required option: " + pref);
            m.endControlFlow();
        }

        // Bind positionals
        if (!params.isEmpty()) {
            m.addStatement("int __pi = 0");
            for (ParamInfo p : params) {
                String fn = p.field.getSimpleName().toString();
                boolean varargs = p.indexRange[1] == -1 || isList(p.fieldType) || isArray(p.fieldType);
                if (varargs) {
                    if (isList(p.fieldType)) {
                        m.addStatement("cmd.$L = new $T<>()", fn, ArrayList.class);
                        m.beginControlFlow("while (__pi < positionals.size())");
                        emitPositionalConvert(m, p, "positionals.get(__pi)", "cmd", fn, true);
                        m.addStatement("__pi++");
                        m.endControlFlow();
                    } else if (isArray(p.fieldType)) {
                        String ct = arrayComponentName(p.fieldType);
                        m.addStatement("$L[] __pa = new $L[positionals.size() - __pi]", ct, ct);
                        m.beginControlFlow("for (int __j = 0; __pi < positionals.size(); __pi++, __j++)");
                        m.addStatement("__pa[__j] = ($L) $T.convert(positionals.get(__pi), $L.class, converters)",
                                boxed(ct), FEMTOCLI_RT, ct);
                        m.endControlFlow();
                        m.addStatement("cmd.$L = __pa", fn);
                    }
                } else {
                    boolean isOpt = p.arity.startsWith("0") || (p.arityRange[0] != -2 && p.arityRange[0] == 0);
                    m.beginControlFlow("if (__pi < positionals.size())");
                    emitPositionalConvert(m, p, "positionals.get(__pi)", "cmd", fn, false);
                    m.addStatement("__pi++");
                    m.endControlFlow();
                    if (!isOpt && NO_DEFAULT.equals(p.defaultValue)) {
                        String lab = p.paramLabel.isEmpty() ? "<" + fn + ">" : p.paramLabel;
                        m.beginControlFlow("else");
                        m.addStatement("throw $T.usageError(cmd, $S)", FEMTOCLI_RT,
                                "Missing required parameter: " + lab);
                        m.endControlFlow();
                    } else if (!NO_DEFAULT.equals(p.defaultValue)) {
                        m.beginControlFlow("else");
                        emitPositionalConvert(m, p, "\"" + escJava(p.defaultValue) + "\"", "cmd", fn, false);
                        m.endControlFlow();
                    }
                }
            }
            // Check for excess positionals when no param is varargs
            boolean hasVarargs = params.stream().anyMatch(p ->
                    p.indexRange[1] == -1 || isList(p.fieldType) || isArray(p.fieldType));
            if (!hasVarargs) {
                m.beginControlFlow("if (__pi < positionals.size())");
                m.addStatement("throw $T.usageError(cmd, $S + positionals.get(__pi))",
                        FEMTOCLI_RT, "Too many parameters: ");
                m.endControlFlow();
            }
        } else {
            m.beginControlFlow("if (!positionals.isEmpty())");
            m.addStatement("throw $T.usageError(cmd, $S + positionals.get(0))",
                    FEMTOCLI_RT, "Unexpected parameter: ");
            m.endControlFlow();
        }

        return m.build();
    }

    /* ================================================================
     *  Subcommand methods
     * ================================================================ */
    private MethodSpec buildResolveSubcommand(List<SubcommandInfo> subs, List<MethodSubcommandInfo> msubs) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("resolveSubcommand")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(String.class).addParameter(String.class, "token");
        for (SubcommandInfo s : subs) {
            m.beginControlFlow("if ($S.equals(token))", s.name);
            m.addStatement("return $S", s.name);
            m.endControlFlow();
        }
        for (MethodSubcommandInfo ms : msubs) {
            m.beginControlFlow("if ($S.equals(token))", ms.name);
            m.addStatement("return $S", ms.name);
            m.endControlFlow();
        }
        m.addStatement("return null");
        return m.build();
    }

    private MethodSpec buildCreateSubcommand(List<SubcommandInfo> subs,
                                             List<MethodSubcommandInfo> msubs,
                                             ClassName parentCN) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("createSubcommand")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(Object.class)
                .addParameter(String.class, "name");
        for (SubcommandInfo s : subs) {
            m.beginControlFlow("if ($S.equals(name))", s.name);
            m.addStatement("return new $L()", s.className);
            m.endControlFlow();
        }
        // Method subcommands don't create separate objects, they are dispatched on the parent
        m.addStatement("return null");
        return m.build();
    }

    private MethodSpec buildSubcommandParser(List<SubcommandInfo> subs) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("subcommandParser")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(COMMAND_PARSER,
                        WildcardTypeName.subtypeOf(Object.class)))
                .addParameter(String.class, "name");
        for (SubcommandInfo s : subs) {
            m.beginControlFlow("if ($S.equals(name))", s.name);
            m.addStatement("return $T.loadParser($L.class)", FEMTOCLI_RT, s.className);
            m.endControlFlow();
        }
        m.addStatement("return null");
        return m.build();
    }

    private MethodSpec buildInvokeMethodSubcommand(ClassName cmdCN,
                                                    List<MethodSubcommandInfo> msubs) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("invokeMethodSubcommand")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(cmdCN, "cmd")
                .addParameter(String.class, "name")
                .addException(Exception.class);
        for (MethodSubcommandInfo ms : msubs) {
            m.beginControlFlow("if ($S.equals(name))", ms.name);
            // Determine return type: int → return directly, void → return 0, other → return 0
            TypeMirror returnType = ms.method.getReturnType();
            if (returnType.getKind() == javax.lang.model.type.TypeKind.INT) {
                m.addStatement("return cmd.$L()", ms.method.getSimpleName());
            } else if (returnType.getKind() == javax.lang.model.type.TypeKind.VOID) {
                m.addStatement("cmd.$L()", ms.method.getSimpleName());
                m.addStatement("return 0");
            } else {
                m.addStatement("Object __r = cmd.$L()", ms.method.getSimpleName());
                m.addStatement("return __r instanceof Integer ? (Integer) __r : 0");
            }
            m.endControlFlow();
        }
        m.addStatement("throw new $T($S + name)", IllegalStateException.class,
                "No method subcommand: ");
        return m.build();
    }

    /* ================================================================
     *  normalizeAgentTokens()
     * ================================================================ */
    private MethodSpec buildNormalizeAgent(List<OptionInfo> allOptions) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("normalizeAgentTokens")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "cmdForErrors")
                .addParameter(DEQUE_STRING, "tokens")
                .addException(Exception.class);

        m.beginControlFlow("if (tokens.isEmpty())").addStatement("return").endControlFlow();
        m.addStatement("$T<String> norm = new $T<>(tokens.size())", List.class, ArrayList.class);

        m.beginControlFlow("for (String t : tokens)");
        // Already prefixed
        m.beginControlFlow("if (t.startsWith($S))", "-");
        m.addStatement("norm.add(t)").addStatement("continue");
        m.endControlFlow();

        // Bare help/version
        m.addStatement("t = $T.normalizeBareHelpOrVersionToken(t)", FEMTOCLI_RT);
        m.beginControlFlow("if ($S.equals(t) || $S.equals(t))", "--help", "--version");
        m.addStatement("norm.add(t)").addStatement("continue");
        m.endControlFlow();

        // Bare name=value
        m.addStatement("int eq = t.indexOf('=')");
        m.beginControlFlow("if (eq >= 0)");
        m.addStatement("String bare = t.substring(0, eq)");

        // Build a map: bare name → canonical option name
        Map<String, String> bareToCanonical = new LinkedHashMap<>();
        for (OptionInfo o : allOptions) {
            for (String n : o.names) {
                String b = n.startsWith("--") ? n.substring(2) : (n.startsWith("-") ? n.substring(1) : n);
                bareToCanonical.putIfAbsent(b, preferredLong(o.names));
            }
        }
        boolean firstB = true;
        for (var e : bareToCanonical.entrySet()) {
            if (firstB) { m.beginControlFlow("if ($S.equals(bare))", e.getKey()); firstB = false; }
            else        { m.nextControlFlow("else if ($S.equals(bare))", e.getKey()); }
            m.addStatement("norm.add($S + t.substring(eq))", e.getValue());
            m.addStatement("continue");
        }
        if (!firstB) m.endControlFlow();
        m.endControlFlow(); // eq >= 0

        // Bare boolean flag (no '=')
        boolean firstBF = true;
        for (OptionInfo o : allOptions) {
            if (!isBooleanType(o.fieldType)) continue;
            for (String n : o.names) {
                String b = n.startsWith("--") ? n.substring(2) : (n.startsWith("-") ? n.substring(1) : n);
                if (firstBF) { m.beginControlFlow("if ($S.equals(t))", b); firstBF = false; }
                else         { m.nextControlFlow("else if ($S.equals(t))", b); }
                m.addStatement("norm.add($S)", n);
                m.addStatement("continue");
            }
        }
        if (!firstBF) m.endControlFlow();

        m.addStatement("norm.add(t)");
        m.endControlFlow(); // for

        m.addStatement("tokens.clear()");
        m.addStatement("tokens.addAll(norm)");
        return m.build();
    }

    /* ================================================================
     *  Field set / conversion helpers
     * ================================================================ */
    private void emitFieldSet(MethodSpec.Builder m, OptionInfo o, String valueExpr) {
        String owner = o.ownerField.isEmpty() ? "cmd" : "cmd." + o.ownerField;
        String fn = o.field.getSimpleName().toString();
        if (isBooleanType(o.fieldType)) {
            // If the value is a literal boolean, assign directly; otherwise parse the string
            if ("true".equals(valueExpr) || "false".equals(valueExpr)) {
                m.addStatement("$L.$L = $L", owner, fn, valueExpr);
            } else {
                m.addStatement("$L.$L = $T.parseBoolean($L)", owner, fn, Boolean.class, valueExpr);
            }
        } else {
            m.addStatement("$L.$L = $L", owner, fn, valueExpr);
        }
    }

    private void emitConvertAndSet(MethodSpec.Builder m, OptionInfo o, String valExpr, boolean verify) {
        String owner = o.ownerField.isEmpty() ? "cmd" : "cmd." + o.ownerField;
        emitConvertAndSetOwner(m, o, valExpr, owner, verify);
    }

    private void emitConvertAndSetOwner(MethodSpec.Builder m, OptionInfo o, String valExpr,
                                        String owner, boolean verify) {
        String fn = o.field.getSimpleName().toString();
        if (!o.converterMethod.isEmpty()) {
            emitConverterMethod(m, o.converterMethod, valExpr, owner, fn);
        } else if (hasConverterClass(o)) {
            m.addStatement("$L.$L = ($T) new $L().convert($L)",
                    owner, fn, TypeName.get(o.fieldType), sourceClassName(o.converterClass), valExpr);
        } else {
            emitBuiltinConvert(m, o.fieldType, owner, fn, valExpr);
        }
        if (verify) emitVerifier(m, o.verifierClass, o.verifierMethod, owner, fn);
    }

    private void emitPositionalConvert(MethodSpec.Builder m, ParamInfo p,
                                        String valExpr, String owner, String fn,
                                        boolean listAdd) {
        if (!p.converterMethod.isEmpty()) {
            if (listAdd) {
                if (p.converterMethod.contains("#")) {
                    String[] parts = p.converterMethod.split("#", 2);
                    String cls = sourceClassName(parts[0]);
                    m.addStatement("$L.$L.add($L.$L($L))", owner, fn, cls, parts[1], valExpr);
                } else {
                    m.addStatement("$L.$L.add($L.$L($L))", owner, fn, owner, p.converterMethod, valExpr);
                }
            } else {
                emitConverterMethod(m, p.converterMethod, valExpr, owner, fn);
            }
        } else if (!p.converterClass.isEmpty() && !p.converterClass.endsWith("NullTypeConverter")) {
            if (listAdd) {
                m.addStatement("$L.$L.add(new $L().convert($L))", owner, fn, sourceClassName(p.converterClass), valExpr);
            } else {
                m.addStatement("$L.$L = ($T) new $L().convert($L)",
                        owner, fn, TypeName.get(p.fieldType), sourceClassName(p.converterClass), valExpr);
            }
        } else {
            if (listAdd) {
                m.addStatement("$L.$L.add(($T) $T.convert($L, $T.class, converters))",
                        owner, fn, String.class, FEMTOCLI_RT, valExpr, String.class);
            } else {
                emitBuiltinConvert(m, p.fieldType, owner, fn, valExpr);
            }
        }
        if (!listAdd) emitVerifier(m, p.verifierClass, p.verifierMethod, owner, fn);
    }

    private void emitConverterMethod(MethodSpec.Builder m, String spec,
                                      String valExpr, String owner, String fn) {
        if (spec.contains("#")) {
            String[] parts = spec.split("#", 2);
            String cls = sourceClassName(parts[0]);
            m.addStatement("$L.$L = $L.$L($L)", owner, fn, cls, parts[1], valExpr);
        } else {
            m.addStatement("$L.$L = $L.$L($L)", owner, fn, owner, spec, valExpr);
        }
    }

    private void emitBuiltinConvert(MethodSpec.Builder m, TypeMirror type,
                                     String owner, String fn, String valExpr) {
        String tn = type.toString();
        if ("java.lang.String".equals(tn)) {
            m.addStatement("$L.$L = $L", owner, fn, valExpr);
        } else {
            // Use the runtime convert() for everything — it checks custom converters first,
            // then built-in converters, then enum matching.
            String boxed = switch (tn) {
                case "int"     -> "java.lang.Integer";
                case "long"    -> "java.lang.Long";
                case "double"  -> "java.lang.Double";
                case "float"   -> "java.lang.Float";
                case "boolean" -> "java.lang.Boolean";
                default        -> tn;
            };
            m.addStatement("$L.$L = ($L) $T.convert($L, $L.class, converters)",
                    owner, fn, boxed, FEMTOCLI_RT, valExpr, boxed);
        }
    }

    private void emitVerifier(MethodSpec.Builder m, String verifierClass, String verifierMethod,
                               String owner, String fn) {
        if (verifierClass != null && !verifierClass.isEmpty() && !verifierClass.endsWith("NullVerifier")) {
            m.beginControlFlow("try");
            m.addStatement("new $L().verify($L.$L)", sourceClassName(verifierClass), owner, fn);
            m.nextControlFlow("catch ($T __ve)", VERIFIER_EX);
            m.addStatement("throw $T.usageError(null, __ve.getMessage())", FEMTOCLI_RT);
            m.endControlFlow();
        }
        if (verifierMethod != null && !verifierMethod.isEmpty()) {
            m.beginControlFlow("try");
            if (verifierMethod.contains("#")) {
                String[] parts = verifierMethod.split("#", 2);
                String cls = sourceClassName(parts[0]);
                m.addStatement("$L.$L($L.$L)", cls, parts[1], owner, fn);
            } else {
                m.addStatement("$L.$L($L.$L)", owner, verifierMethod, owner, fn);
            }
            m.nextControlFlow("catch ($T __ve)", VERIFIER_EX);
            m.addStatement("throw $T.usageError(null, __ve.getMessage())", FEMTOCLI_RT);
            m.endControlFlow();
        }
    }

    /* ================================================================
     *  Type utilities
     * ================================================================ */
    private boolean isBooleanType(TypeMirror t) {
        String n = t.toString();
        return "boolean".equals(n) || "java.lang.Boolean".equals(n);
    }
    private boolean isArray(TypeMirror t) { return t.getKind() == TypeKind.ARRAY; }
    private boolean isList(TypeMirror t) {
        TypeElement lt = elements.getTypeElement("java.util.List");
        return lt != null && types.isAssignable(types.erasure(t), types.erasure(lt.asType()));
    }
    private String arrayComponentName(TypeMirror t) {
        if (t instanceof ArrayType at) return at.getComponentType().toString();
        return "Object";
    }
    private String listElementTypeName(TypeMirror listType) {
        if (listType instanceof DeclaredType dt) {
            var args = dt.getTypeArguments();
            if (!args.isEmpty()) return args.get(0).toString();
        }
        return "java.lang.String";
    }
    private boolean hasCustomConverter(OptionInfo o) {
        return !o.converterMethod.isEmpty() || hasConverterClass(o);
    }
    private boolean hasConverterClass(OptionInfo o) {
        return !o.converterClass.isEmpty() && !o.converterClass.endsWith("NullTypeConverter");
    }
    private boolean hasShort(String[] names) {
        for (String n : names)
            if (n.length() == 2 && n.charAt(0) == '-' && Character.isLetter(n.charAt(1))) return true;
        return false;
    }
    /** Returns the last element of a String array. */
    private static String last(String[] arr) { return arr[arr.length - 1]; }

    /** Strips leading '-' characters from an option name. */
    private static String stripLeadingDashes(String s) {
        int i = 0; while (i < s.length() && s.charAt(i) == '-') i++;
        return s.substring(i);
    }

    /** Builds a user-facing label for a @Parameters field. */
    private String paramLabel(ParamInfo p) {
        String label = p.paramLabel.isEmpty()
                ? "<" + p.field.getSimpleName() + ">" : p.paramLabel;
        boolean varargs = p.indexRange[1] == -1 || isList(p.fieldType) || isArray(p.fieldType);
        boolean optional = p.arity.startsWith("0") || (p.arityRange[0] == 0);
        if (varargs) label = "[" + label + "...]";
        else if (optional) label = "[" + label + "]";
        return label;
    }

    private String preferredLong(String[] names) {
        for (String n : names) if (n.startsWith("--")) return n;
        return names[0];
    }
    private String boxed(String prim) {
        return switch (prim) {
            case "int" -> "Integer"; case "long" -> "Long"; case "double" -> "Double";
            case "float" -> "Float"; case "boolean" -> "Boolean"; case "byte" -> "Byte";
            case "short" -> "Short"; case "char" -> "Character"; default -> prim;
        };
    }

    /* ================================================================
     *  Placeholder expansion (compile-time)
     * ================================================================ */
    private String expandPlaceholders(OptionInfo o) {
        String d = o.description;
        String def = NO_DEFAULT.equals(o.defaultValue) ? "none" : o.defaultValue;
        d = d.replace("${DEFAULT-VALUE}", def);
        if (d.contains("${COMPLETION-CANDIDATES")) d = expandCandidates(d, o);
        return d;
    }

    private String expandCandidates(String desc, OptionInfo o) {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (idx < desc.length()) {
            int s = desc.indexOf("${COMPLETION-CANDIDATES", idx);
            if (s == -1) { sb.append(desc.substring(idx)); break; }
            sb.append(desc, idx, s);
            int e = desc.indexOf("}", s);
            if (e == -1) { sb.append(desc.substring(s)); break; }
            String joiner = ", ";
            int ci = desc.indexOf(":", s + "${COMPLETION-CANDIDATES".length());
            if (ci != -1 && ci < e) joiner = desc.substring(ci + 1, e).replace("\\n", "\n").replace("\\t", "\t");
            sb.append(enumCandidates(o, joiner));
            idx = e + 1;
        }
        return sb.toString();
    }

    private String enumCandidates(OptionInfo o, String joiner) {
        Element el = types.asElement(o.fieldType);
        if (el == null || el.getKind() != ElementKind.ENUM) return "";
        StringJoiner sj = new StringJoiner(joiner);
        for (Element enc : ((TypeElement) el).getEnclosedElements()) {
            if (enc.getKind() != ElementKind.ENUM_CONSTANT) continue;
            String name = enc.getSimpleName().toString().toLowerCase(Locale.ROOT);
            if (o.showEnumDescriptions) {
                AnnotationMirror da = findAnnotation(enc, ANNO_PKG + ".Description");
                if (da != null) {
                    String dv = annoString(da, "value");
                    if (!dv.isEmpty()) { sj.add(name + " (" + dv + ")"); continue; }
                }
                // If @Description missing, just use the name (backwards compatible)
                sj.add(name);
            } else {
                sj.add(name);
            }
        }
        return sj.toString();
    }

    /* ================================================================
     *  Misc helpers
     * ================================================================ */

    /**
     * Convert a class reference to a valid Java source expression.
     * Handles: fully-qualified binary names (replace $ with .), qualified source names,
     * and unqualified simple names (resolved against the current command type hierarchy).
     */
    private String sourceClassName(String name) {
        // Already qualified (has dots or $) — just swap $ → .
        if (name.contains(".") || name.contains("$")) {
            return name.replace('$', '.');
        }
        // Unqualified simple name — resolve against command type hierarchy
        if (currentCommandType != null) {
            // 1. Inner type of the command class itself
            for (Element enc : currentCommandType.getEnclosedElements()) {
                if (enc instanceof TypeElement te && te.getSimpleName().toString().equals(name)) {
                    return te.getQualifiedName().toString();
                }
            }
            // 2. Walk up enclosing classes
            Element enclosing = currentCommandType.getEnclosingElement();
            while (enclosing != null) {
                if (enclosing instanceof TypeElement te) {
                    for (Element enc : te.getEnclosedElements()) {
                        if (enc instanceof TypeElement enclosed &&
                                enclosed.getSimpleName().toString().equals(name)) {
                            return enclosed.getQualifiedName().toString();
                        }
                    }
                }
                enclosing = enclosing.getEnclosingElement();
            }
        }
        // 3. Try as a top-level class in the same package
        if (currentCommandType != null) {
            String pkg = "";
            Element pkgEl = currentCommandType.getEnclosingElement();
            while (pkgEl != null && !(pkgEl instanceof javax.lang.model.element.PackageElement)) {
                pkgEl = pkgEl.getEnclosingElement();
            }
            if (pkgEl instanceof javax.lang.model.element.PackageElement pe) {
                pkg = pe.getQualifiedName().toString();
            }
            String fqn = pkg.isEmpty() ? name : pkg + "." + name;
            TypeElement resolved = elements.getTypeElement(fqn);
            if (resolved != null) return fqn;
        }
        // Fallback — return as-is
        return name;
    }

    private static String firstOf(String[] arr) { return arr.length > 0 ? arr[0] : ""; }

    private static String strArrLit(String[] arr) {
        if (arr == null || arr.length == 0) return "new String[0]";
        StringBuilder sb = new StringBuilder("new String[]{");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(escJava(arr[i])).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static int[] parseRange(String range) {
        if (range == null || range.isEmpty()) return new int[]{-2, -2};
        if (range.contains("..")) {
            String[] p = range.split("\\.\\.");
            return new int[]{ Integer.parseInt(p[0]), "*".equals(p[1]) ? -1 : Integer.parseInt(p[1]) };
        }
        int v = Integer.parseInt(range);
        return new int[]{v, v};
    }
}
