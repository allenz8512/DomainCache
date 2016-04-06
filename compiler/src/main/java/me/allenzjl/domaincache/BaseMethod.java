package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * The type Base method.
 */
public class BaseMethod {

    public static final ClassName CACHE_STORAGE_TYPE = ClassName.get("me.allenzjl.domaincache", "CacheStorage");

    protected String mPackageName;

    protected String mClassName;

    protected String mClassQualifiedName;

    protected ExecutableElement mMethodElement;

    protected String mMethodName;

    protected List<? extends VariableElement> mParameters;

    protected String mSignature;

    protected String mQualifiedName;

    protected TypeName mReturnType;

    protected CacheEvict mCacheEvict;

    protected String mExpireAlias;

    protected Map<String, Set<AdditionalParameter>> mCacheParameters;

    public BaseMethod(String packageName, String className, ExecutableElement methodElement) {
        mPackageName = packageName;
        mClassName = className;
        mClassQualifiedName = packageName + "." + className;
        mMethodElement = methodElement;
        mMethodName = mMethodElement.getSimpleName().toString();
        mParameters = mMethodElement.getParameters();
        mSignature = generateSignature();
        mQualifiedName = mClassQualifiedName + "." + mSignature;
        processCacheEvictAnnotation();
    }

    protected String generateSignature() {
        StringBuilder builder = new StringBuilder(mMethodName);
        builder.append("(");
        int size = mParameters.size();
        for (int i = 0; i < size; i++) {
            VariableElement paramElement = mParameters.get(i);
            String paramClassName = TypeName.get(paramElement.asType()).toString();
            builder.append(paramClassName);
            if (i < size - 1) {
                builder.append(",");
            }
        }
        builder.append(")");
        return builder.toString();
    }

    protected void processCacheEvictAnnotation() {
        mCacheEvict = mMethodElement.getAnnotation(CacheEvict.class);
        if (mCacheEvict != null) {
            if (ProcessUtils.isStringEmpty(mCacheEvict.value())) {
                ProcessUtils.printError("Value of attribute 'value' should not be empty", mMethodElement);
            }
            mExpireAlias = mCacheEvict.value();
        }
    }

    public String getMethodName() {
        return mMethodName;
    }

    public String getQualifiedName() {
        return mQualifiedName;
    }

    protected String buildParamNames() {
        StringBuilder builder = new StringBuilder();
        int size = mParameters.size();
        for (int i = 0; i < size; i++) {
            VariableElement paramElement = mParameters.get(i);
            builder.append(paramElement.getSimpleName().toString());
            if (i < size - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    public MethodSpec generateMethod(Map<String, Set<AdditionalParameter>> cacheParameters) {
        mCacheParameters = cacheParameters;
        initReturnType();
        MethodSpec.Builder methodBuilder = ProcessUtils.overrideMethod(mMethodElement);
        addCacheEvict(methodBuilder);
        addMethodContent(methodBuilder);
        return methodBuilder.build();
    }

    protected void addCacheEvict(MethodSpec.Builder methodBuilder) {
        if (mExpireAlias != null) {
            String aliasName = mMethodName + "_a";
            methodBuilder.addStatement("String $N = $S", aliasName, mExpireAlias);
            methodBuilder.addStatement("$T.getInstance().remove($N)", CACHE_STORAGE_TYPE, aliasName);
        }
    }

    protected void addMethodContent(MethodSpec.Builder methodBuilder) {
        String paramNames = buildParamNames();
        if (mReturnType != TypeName.VOID.box()) {
            methodBuilder.addStatement("return super.$N($N)", mMethodName, paramNames);
        } else {
            methodBuilder.addStatement("super.$N($N)", mMethodName, paramNames);
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

        CacheMethod that = (CacheMethod) o;

        return mMethodName != null ? mMethodName.equals(that.mMethodName) :
                that.mMethodName == null && (mSignature != null ? mSignature.equals(that.mSignature) : that.mSignature == null);

    }

    @Override
    public int hashCode() {
        int result = mMethodName != null ? mMethodName.hashCode() : 0;
        result = 31 * result + (mSignature != null ? mSignature.hashCode() : 0);
        return result;
    }

    protected void initReturnType() {
        TypeMirror returnType = mMethodElement.getReturnType();
        mReturnType = TypeName.get(returnType);
        if (mReturnType.isPrimitive() || mReturnType == TypeName.VOID) {
            mReturnType = mReturnType.box();
        }
    }
}
