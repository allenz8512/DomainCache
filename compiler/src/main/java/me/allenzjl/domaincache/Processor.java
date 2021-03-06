package me.allenzjl.domaincache;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * 缓存注解处理器。
 */
@SuppressWarnings("unused")
@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

    protected Map<String, CacheClass> mCacheClasses;

    protected Map<String, ObservableClass> mObservableClasses;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(CacheableDomain.class.getCanonicalName());
        return Collections.unmodifiableSet(annotations);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessUtils.init(processingEnv);
        mCacheClasses = new HashMap<>();
        mObservableClasses = new HashMap<>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return doProcess(roundEnv);
        } catch (Exception e) {
            e.printStackTrace();
            if (!(e instanceof ProcessException)) {
                ProcessUtils.printError(e.getMessage(), null);
            } else {
                throw e;
            }
        }
        return true;
    }

    protected boolean doProcess(RoundEnvironment roundEnv) {
        Set<? extends Element> cacheableDomainElements = roundEnv.getElementsAnnotatedWith(CacheableDomain.class);
        for (Element cacheableDomainElement : cacheableDomainElements) {
            verifyCacheableDomainElement(cacheableDomainElement);
            markCacheClass((TypeElement) cacheableDomainElement);
        }

        for (CacheClass cacheClass : mCacheClasses.values()) {
            markMethods(cacheClass);
        }

        for (CacheClass cacheClass : mCacheClasses.values()) {
            cacheClass.generateJavaFile();
        }

        for (ObservableClass observableClass : mObservableClasses.values()) {
            observableClass.generateJavaFile();
        }

        roundClean();
        return false;
    }

    protected void roundClean() {
        mCacheClasses.clear();
        mObservableClasses.clear();
    }

    protected void verifyCacheableDomainElement(Element e) {
        if (!e.getModifiers().contains(Modifier.PUBLIC)) {
            ProcessUtils.printError("@CacheableDomain should annotate a public class", e);
        }
        if (e.getModifiers().contains(Modifier.FINAL)) {
            ProcessUtils.printError("@CacheableDomain should not annotate a final class", e);
        }
        if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            ProcessUtils.printError("@CacheableDomain should not annotate a abstract class", e);
        }
        if (((TypeElement) e).getNestingKind().isNested()) {
            ProcessUtils.printError("@CacheableDomain should not annotate a nested class", e);
        }
    }

    protected void markCacheClass(TypeElement cacheableDomainElement) {
        CacheClass cacheClass = new CacheClass(cacheableDomainElement);
        String qualifiedName = cacheClass.getQualifiedName();
        if (!mCacheClasses.containsKey(qualifiedName)) {
            mCacheClasses.put(qualifiedName, cacheClass);
        }
    }

    protected void markMethods(CacheClass cacheClass) {
        TypeElement classElement = cacheClass.getClassElement();
        List<? extends Element> enclosedElements = classElement.getEnclosedElements();
        for (Element element : enclosedElements) {
            if (element.getKind() == ElementKind.FIELD) {
                if (isCacheParameterElement(element)) {
                    verifyCacheParameterElement(element);
                    cacheClass.addCacheParameter(new AdditionalParameter(element));
                }
            } else if (element.getKind() == ElementKind.METHOD) {
                ExecutableElement methodElement = ((ExecutableElement) element);
                String packageName = cacheClass.getPackageName();
                String className = cacheClass.getClassName();
                if (isCacheableElement(methodElement)) {
                    verifyCacheableElement(methodElement);
                    cacheClass.addMethod(new CacheMethod(packageName, className, methodElement));
                } else if (isCacheParameterElement(methodElement)) {
                    verifyCacheParameterElement(methodElement);
                    cacheClass.addCacheParameter(new AdditionalParameter(methodElement));
                } else if (isCacheEvictElement(methodElement)) {
                    verifyCacheEvictElement(methodElement);
                    cacheClass.addMethod(new CacheEvictMethod(packageName, className, methodElement));
                }

                if (isCacheObservableElement(methodElement)) {
                    verifyCacheObservableElement(methodElement);
                    markObservableMethod(classElement, methodElement);
                }
            }
        }
    }

    private boolean isCacheableElement(ExecutableElement e) {
        return e.getAnnotation(Cacheable.class) != null;
    }

    protected boolean isCacheParameterElement(Element e) {
        return e.getAnnotation(CacheParameter.class) != null;
    }

    protected void verifyCacheParameterElement(Element e) {
        if (e.getModifiers().contains(Modifier.PRIVATE)) {
            ProcessUtils.printError("@CacheParameter should not annotate a private method/field", e);
        }
        if (e.getModifiers().contains(Modifier.STATIC)) {
            ProcessUtils.printError("@CacheParameter should not annotate a static method/field", e);
        }
        if (e.getKind() == ElementKind.METHOD) {
            if (e.getAnnotation(Cacheable.class) != null) {
                ProcessUtils.printError("Method annotated by @CacheParameter should not annotated by @Cacheable", e);
            }
            if (e.getAnnotation(CacheEvict.class) != null) {
                ProcessUtils.printError("Method annotated by @CacheParameter should not annotated by @CacheEvict", e);
            }
            if (e.getAnnotation(CacheObservable.class) != null) {
                ProcessUtils.printError("Method annotated by @CacheParameter should not annotated by @CacheObservable", e);
            }
            if (!((ExecutableElement) e).getParameters().isEmpty()) {
                ProcessUtils.printError("Method annotated by @CacheParameter should not have any parameter", e);
            }
            if (((ExecutableElement) e).getReturnType().getKind() == TypeKind.VOID) {
                ProcessUtils.printError("Method annotated by @CacheParameter should not return 'Void'", e);
            }
        } else {
            if (e.asType().getKind() == TypeKind.VOID) {
                ProcessUtils.printError("Field annotated by @CacheParameter should not be 'Void' type", e);
            }
        }
    }

    protected void verifyCacheableElement(ExecutableElement e) {
        if (e.getModifiers().contains(Modifier.PRIVATE)) {
            ProcessUtils.printError("@Cacheable should not annotate a private method", e);
        }
        if (e.getModifiers().contains(Modifier.FINAL)) {
            ProcessUtils.printError("@Cacheable should not annotate a final method", e);
        }
        if (e.getModifiers().contains(Modifier.STATIC)) {
            ProcessUtils.printError("@Cacheable should not annotate a static method", e);
        }
        if (e.getReturnType().getKind() == TypeKind.VOID) {
            ProcessUtils.printError("Method annotated by @Cacheable should return something", e);
        }
        if (e.getAnnotation(CacheParameter.class) != null) {
            ProcessUtils.printError("Method annotated by @Cacheable should not annotated by @CacheParameter", e);
        }
        TypeMirror returnType = e.getReturnType();
        TypeName returnTypeName = TypeName.get(returnType);
        if (returnTypeName instanceof ParameterizedTypeName) {
            ClassName rawTypeName = ClassName.get((TypeElement) ((DeclaredType) returnType).asElement());
            if (!rawTypeName.equals(ClassName.get(List.class)) && !rawTypeName.equals(ClassName.get(ArrayList.class))) {
                ProcessUtils.printError("Unsupported return type of method annotated by @Cacheable", e);
            }
        } else if (returnTypeName instanceof TypeVariableName || returnTypeName instanceof WildcardTypeName) {
            ProcessUtils.printError("Unsupported return type of method annotated by @Cacheable", e);
        }
    }

    protected boolean isCacheEvictElement(ExecutableElement e) {
        return e.getAnnotation(CacheEvict.class) != null;
    }

    protected void verifyCacheEvictElement(ExecutableElement e) {
        if (e.getModifiers().contains(Modifier.PRIVATE)) {
            ProcessUtils.printError("@CacheEvict should not annotate a private method", e);
        }
        if (e.getModifiers().contains(Modifier.FINAL)) {
            ProcessUtils.printError("@CacheEvict should not annotate a final method", e);
        }
        if (e.getModifiers().contains(Modifier.STATIC)) {
            ProcessUtils.printError("@CacheEvict should not annotate a static method", e);
        }
        if (e.getAnnotation(CacheParameter.class) != null) {
            ProcessUtils.printError("Method annotated by @CacheEvict should not annotated by @CacheParameter", e);
        }
    }

    protected boolean isCacheObservableElement(ExecutableElement e) {
        return e.getAnnotation(CacheObservable.class) != null;
    }

    protected void verifyCacheObservableElement(ExecutableElement e) {
        if (e.getModifiers().contains(Modifier.PRIVATE)) {
            ProcessUtils.printError("@CacheObservable should not annotate a private method", e);
        }
        if (e.getModifiers().contains(Modifier.FINAL)) {
            ProcessUtils.printError("@CacheObservable should not annotate a final method", e);
        }
        if (e.getModifiers().contains(Modifier.STATIC)) {
            ProcessUtils.printError("@CacheObservable should not annotate a static method", e);
        }
        if (e.getAnnotation(CacheParameter.class) != null) {
            ProcessUtils.printError("Method annotated by @CacheObservable should not annotated by @CacheParameter", e);
        }
    }

    protected void markCacheMethod(ExecutableElement cacheMethodElement) {
        TypeElement classElement = (TypeElement) cacheMethodElement.getEnclosingElement();
        String qualifiedName = ProcessUtils.getTypeQualifiedName(classElement) + CacheClass.CACHE_CLASS_NAME_SUFFIX;
        if (!mCacheClasses.containsKey(qualifiedName)) {
            ProcessUtils.printError("Method annotated by @Cacheable should be inside a class annotated by @CacheableDomain",
                    cacheMethodElement);
        }
        CacheClass cacheClass = mCacheClasses.get(qualifiedName);
        cacheClass.addMethod(new CacheMethod(cacheClass.getPackageName(), cacheClass.getClassName(), cacheMethodElement));
    }

    protected void markObservableMethod(TypeElement classElement, ExecutableElement methodElement) {
        ObservableClass observableClass = new ObservableClass(classElement);
        String qualifiedName = observableClass.getQualifiedName();
        if (mObservableClasses.containsKey(qualifiedName)) {
            observableClass = mObservableClasses.get(qualifiedName);
        } else {
            mObservableClasses.put(qualifiedName, observableClass);
        }
        observableClass
                .addMethod(new ObservableMethod(observableClass.getPackageName(), observableClass.getClassName(), methodElement));
    }
}
