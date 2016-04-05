package me.allenzjl.domaincache;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * The type Cache class.
 */
public class CacheClass {

    public static final String CACHE_CLASS_NAME_SUFFIX = "Cacheable";

    public static final String CLIENT_FIELD_NAME = "client";

    protected TypeElement mClassElement;

    protected String mPackageName;

    protected String mClassName;

    protected Set<AbstractMethod> mMethods;

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

    public void addMethod(AbstractMethod cacheMethod) {
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
        TypeSpec.Builder cacheClassBuilder = TypeSpec.classBuilder(mClassName).addModifiers(Modifier.PUBLIC);
        TypeName clientClassType = TypeName.get(mClassElement.asType());
        // 添加要代理的字段
        FieldSpec clientField = FieldSpec.builder(clientClassType, CLIENT_FIELD_NAME, Modifier.PROTECTED).build();
        cacheClassBuilder.addField(clientField);
        // 添加构造方法
        MethodSpec constructor =
                MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addParameter(clientClassType, CLIENT_FIELD_NAME)
                        .addStatement("this.$N = $N", CLIENT_FIELD_NAME, CLIENT_FIELD_NAME).build();
        cacheClassBuilder.addMethod(constructor);
        // 添加方法
        for (AbstractMethod method : mMethods) {
            cacheClassBuilder.addMethod(method.generateMethod(mCacheParameters));
        }
        return cacheClassBuilder.build();
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

        if (mPackageName != null ? !mPackageName.equals(that.mPackageName) : that.mPackageName != null) {
            return false;
        }
        return mClassName != null ? mClassName.equals(that.mClassName) : that.mClassName == null;

    }

    @Override
    public int hashCode() {
        int result = mPackageName != null ? mPackageName.hashCode() : 0;
        result = 31 * result + (mClassName != null ? mClassName.hashCode() : 0);
        return result;
    }
}
