package me.allenzjl.domaincache;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * The type Cache class.
 */
public class CacheClass {

    public static final String CACHE_CLASS_NAME_SUFFIX = "Cacheable";

    protected TypeElement mClassElement;

    protected String mPackageName;

    protected String mClassName;

    protected Set<BasicMethod> mMethods;

    protected boolean mJavaFileGenerated = false;

    protected Map<String, Set<AdditionalParameter>> mCacheParameters;

    public CacheClass(TypeElement classElement) {
        mClassElement = classElement;
        mPackageName = ProcessUtils.getProcessingEnv().getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        mClassName = classElement.getSimpleName() + CACHE_CLASS_NAME_SUFFIX;
        mMethods = new LinkedHashSet<>();
        mCacheParameters = new HashMap<>();
    }

    public TypeElement getClassElement() {
        return mClassElement;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getClassName() {
        return mClassName;
    }

    public String getQualifiedName() {
        return mPackageName + "." + mClassName;
    }

    public Map<String, Set<AdditionalParameter>> getCacheParameters() {
        return mCacheParameters;
    }

    public void addMethod(BasicMethod cacheMethod) {
        if (!mMethods.contains(cacheMethod)) {
            mMethods.add(cacheMethod);
        }
    }

    public void addCacheParameter(AdditionalParameter cacheParameter) {
        for (Set<AdditionalParameter> set : mCacheParameters.values()) {
            if (set.contains(cacheParameter)) {
                ProcessUtils.printError("Names of method/field annotated by @CacheParameter are duplicated",
                        cacheParameter.getElement());
            }
        }
        String alias = cacheParameter.getAliasPrefix();
        Set<AdditionalParameter> set;
        if (!mCacheParameters.containsKey(alias)) {
            set = new HashSet<>();
            mCacheParameters.put(alias, set);
        } else {
            set = mCacheParameters.get(alias);
        }
        set.add(cacheParameter);
    }

    public boolean isJavaFileGenerated() {
        return mJavaFileGenerated;
    }

    protected TypeSpec generateCacheClass() {
        TypeName clientClassType = TypeName.get(mClassElement.asType());
        TypeSpec.Builder cacheClassBuilder =
                TypeSpec.classBuilder(mClassName).addModifiers(Modifier.PUBLIC).superclass(clientClassType);
        addCacheClassConstructors(cacheClassBuilder);
        for (BasicMethod method : mMethods) {
            if (method instanceof CacheMethod) {
                ((CacheMethod) method).setCacheParameters(mCacheParameters);
            }
            cacheClassBuilder.addMethod(method.generateMethod());
        }
        return cacheClassBuilder.build();
    }

    protected void addCacheClassConstructors(TypeSpec.Builder classBuilder) {
        List<? extends Element> elements = mClassElement.getEnclosedElements();
        boolean containNonPublicConstructor = false;
        boolean containPublicConstructor = false;
        for (Element e : elements) {
            if (e.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) e;
            if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                MethodSpec.Builder constructorBuilder = ProcessUtils.overrideConstructor(constructor);
                classBuilder.addMethod(constructorBuilder.build());
                containPublicConstructor = true;
            } else {
                containNonPublicConstructor = true;
            }
        }
        if (containNonPublicConstructor && !containPublicConstructor) {
            ProcessUtils
                    .printError("Class annotated by @CacheableDomain should have at least one public constructor", mClassElement);
        }
    }

    public void generateJavaFile() {
        if (mJavaFileGenerated) {
            return;
        }
        TypeSpec cacheClassSpec = generateCacheClass();
        JavaFile javaFile = JavaFile.builder(mPackageName, cacheClassSpec).build();
        try {
            javaFile.writeTo(ProcessUtils.getProcessingEnv().getFiler());
            mJavaFileGenerated = true;
        } catch (IOException e) {
            throw new ProcessException(e, null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheClass that = (CacheClass) o;

        return mPackageName != null ? mPackageName.equals(that.mPackageName) :
                that.mPackageName == null && (mClassName != null ? mClassName.equals(that.mClassName) : that.mClassName == null);

    }

    @Override
    public int hashCode() {
        int result = mPackageName != null ? mPackageName.hashCode() : 0;
        result = 31 * result + (mClassName != null ? mClassName.hashCode() : 0);
        return result;
    }
}
