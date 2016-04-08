package me.allenzjl.domaincache;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.HashMap;
import java.util.HashSet;
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
public class CacheClass extends BasicClass {

    public static final String CACHE_CLASS_NAME_SUFFIX = "Cacheable";

    protected Map<String, Set<AdditionalParameter>> mCacheParameters;

    public CacheClass(TypeElement classElement) {
        super(classElement, CACHE_CLASS_NAME_SUFFIX);
        mCacheParameters = new HashMap<>();
    }

    public Map<String, Set<AdditionalParameter>> getCacheParameters() {
        return mCacheParameters;
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

    @Override
    protected TypeSpec generateClass() {
        TypeName clientClassType = TypeName.get(mClassElement.asType());
        TypeSpec.Builder cacheClassBuilder =
                TypeSpec.classBuilder(mClassName).addModifiers(Modifier.PUBLIC).superclass(clientClassType);
        addCacheClassConstructors(cacheClassBuilder);
        for (BasicMethod method : mMethods) {
            if (method instanceof CacheMethod) {
                ((CacheMethod) method).setCacheParameters(mCacheParameters);
            }
            for (MethodSpec methodSpec : method.generateMethods()) {
                cacheClassBuilder.addMethod(methodSpec);
            }
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
}
