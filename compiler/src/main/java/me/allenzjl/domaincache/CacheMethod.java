package me.allenzjl.domaincache;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

/**
 * The type Cache method.
 */
public class CacheMethod extends CacheEvictMethod {

    public static final int RETURN_TYPE_FORM_OBJECT = 0;

    public static final int RETURN_TYPE_FORM_LIST = 1;

    public static final int RETURN_TYPE_FORM_ARRAY = 2;

    protected Cacheable mCacheable;

    protected String mCacheAlias;

    protected int mExpire;

    protected List<AdditionalParameter> mAdditionalParameters;

    protected int mReturnTypeForm;

    protected TypeName mResultType;

    public CacheMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
        processCacheableAnnotation();
    }

    protected void processCacheableAnnotation() {
        mCacheable = mMethodElement.getAnnotation(Cacheable.class);
        mCacheAlias = mCacheable.value();
        if (ProcessUtils.isStringEmpty(mCacheAlias)) {
            mCacheAlias = "";
        }
        int expire = mCacheable.expire();
        if (expire < 0) {
            ProcessUtils.printError("Value of attribute 'expire' should not below zero", mMethodElement);
        }
        mExpire = expire;
    }

    protected void initResultType() {
        if (mReturnType instanceof ClassName) {
            mResultType = mReturnType;
            mReturnTypeForm = RETURN_TYPE_FORM_OBJECT;
        } else if (mReturnType instanceof ParameterizedTypeName) {
            mResultType = ((ParameterizedTypeName) mReturnType).typeArguments.get(0);
            mReturnTypeForm = RETURN_TYPE_FORM_LIST;
        } else if (mReturnType instanceof ArrayTypeName) {
            mResultType = ((ArrayTypeName) mReturnType).componentType;
            mReturnTypeForm = RETURN_TYPE_FORM_ARRAY;
        } else {
            throw new IllegalStateException("Should not go here");
        }
    }

    protected void intAdditionalParameters() {
        mAdditionalParameters = new ArrayList<>();
        Set<AdditionalParameter> globalParameters = mCacheParameters.get("");
        if (globalParameters != null) {
            mAdditionalParameters.addAll(globalParameters);
        }
        if (!mCacheAlias.equals("")) {
            for (String alias : mCacheParameters.keySet()) {
                if (!alias.equals("") && mCacheAlias.startsWith(alias)) {
                    mAdditionalParameters.addAll(mCacheParameters.get(alias));
                }
            }
        }
        Collections.sort(mAdditionalParameters, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    }

    protected int parameterCount() {
        return mAdditionalParameters.size() + mParameters.size();
    }

    @Override
    protected void addMethodContent(MethodSpec.Builder methodBuilder) {
        initResultType();
        String keyName = mMethodName + "_k";
        String jsonObjectName = mMethodName + "_p";
        String resultName = mMethodName + "_r";
        methodBuilder.addStatement("String $N = $S", keyName, mQualifiedName);
        intAdditionalParameters();
        int paramCount = parameterCount();
        if (paramCount == 0) {
            methodBuilder.addStatement("Object $N = null", jsonObjectName);
        } else {
            ClassName jsonObjectTypeName = ClassName.get("com.alibaba.fastjson", "JSONObject");
            methodBuilder.addStatement("$T $N = new $T()", jsonObjectTypeName, jsonObjectName, jsonObjectTypeName);
            for (VariableElement paramElement : mParameters) {
                String paramName = paramElement.getSimpleName().toString();
                methodBuilder.addStatement("$N.put($S, $N)", jsonObjectName, paramName, paramName);
            }
            for (AdditionalParameter parameter : mAdditionalParameters) {
                String paramName = mClassName + "_" + parameter.getName();
                String statement = "$N.put($S, $N";
                if (parameter.isMethod()) {
                    statement += "())";
                } else {
                    statement += ")";
                }
                methodBuilder.addStatement(statement, jsonObjectName, paramName, parameter.getName());
            }
        }
        String getMethodName;
        if (mReturnTypeForm == RETURN_TYPE_FORM_LIST) {
            getMethodName = "getList";
        } else if (mReturnTypeForm == RETURN_TYPE_FORM_ARRAY) {
            getMethodName = "getArray";
        } else {
            getMethodName = "getObject";
        }
        methodBuilder.addStatement("$T $N = $T.getInstance().$N($N, $N, $T.class)", mReturnType, resultName, CACHE_STORAGE_TYPE,
                getMethodName, keyName, jsonObjectName, mResultType);
        methodBuilder.beginControlFlow("if ($N == null)", resultName);
        methodBuilder.addStatement("$N = super.$N($N)", resultName, mMethodName, buildParamNames());
        methodBuilder
                .addStatement("$T.getInstance().put($N, $N, $N, $S, $L)", CACHE_STORAGE_TYPE, keyName, jsonObjectName, resultName,
                        mCacheAlias, mExpire);
        methodBuilder.endControlFlow();
        methodBuilder.addStatement("return $N", resultName);
    }

}
