package me.allenzjl.domaincache;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeVariable;

/**
 * The type Cache method.
 */
public class CacheMethod extends CacheEvictMethod {

    public static final String METHOD_PART_A_SUFFIX = "_A";

    public static final String METHOD_PART_B_SUFFIX = "_B";

    public static final int RETURN_TYPE_FORM_OBJECT = 0;

    public static final int RETURN_TYPE_FORM_LIST = 1;

    public static final int RETURN_TYPE_FORM_ARRAY = 2;

    protected Cacheable mCacheable;

    protected String mCacheAlias;

    protected int mExpire;

    protected List<AdditionalParameter> mAdditionalParameters;

    protected int mReturnTypeForm;

    protected TypeName mResultType;

    protected Map<String, Set<AdditionalParameter>> mCacheParameters;

    protected String mKeyName;

    protected String mJsonObjectName;

    protected String mResultName;

    protected CacheObservable mCacheObservable;

    protected int mCacheStrategy;

    public CacheMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
        mKeyName = mMethodName + "_k";
        mJsonObjectName = mMethodName + "_p";
        mResultName = mMethodName + "_r";
        processCacheableAnnotation();
        processCacheObservableAnnotation();
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

    protected void processCacheObservableAnnotation() {
        mCacheStrategy = CacheStrategy.READ_CACHE_ONLY;
        mCacheObservable = mMethodElement.getAnnotation(CacheObservable.class);
        if (mCacheObservable != null) {
            int strategy = mCacheObservable.value();
            if (strategy != CacheStrategy.READ_CACHE_ONLY & strategy != CacheStrategy.PUSH_CACHE_FIRST) {
                ProcessUtils
                        .printError("Value of attribute 'value' should be READ_CACHE_ONLY or PUSH_CACHE_FIRST", mMethodElement);
            }
            mCacheStrategy = strategy;
        }
    }

    public void setCacheParameters(Map<String, Set<AdditionalParameter>> cacheParameters) {
        mCacheParameters = cacheParameters;
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
    public List<MethodSpec> generateMethods() {
        initResultType();
        List<MethodSpec> list = new ArrayList<>();
        list.add(generateMethod());
        if (mCacheStrategy == CacheStrategy.PUSH_CACHE_FIRST) {
            list.add(generateMethodPartA());
            list.add(generateMethodPartB());
        }
        return list;
    }

    protected MethodSpec.Builder copyMethodSignatureBuilder(ExecutableElement methodElement, String methodName) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

        for (TypeParameterElement typeParameterElement : methodElement.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

        methodBuilder.returns(mReturnType);

        List<? extends VariableElement> parameters = methodElement.getParameters();
        for (VariableElement parameter : parameters) {
            TypeName type = TypeName.get(parameter.asType());
            String name = parameter.getSimpleName().toString();
            Set<Modifier> parameterModifiers = parameter.getModifiers();
            if (!parameterModifiers.contains(Modifier.FINAL)) {
                parameterModifiers = new HashSet<>(parameterModifiers);
                parameterModifiers.add(Modifier.FINAL);
            }
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
                    .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
            methodBuilder.addParameter(parameterBuilder.build());
        }
        methodBuilder.varargs(methodElement.isVarArgs());
        return methodBuilder;
    }

    protected MethodSpec generateMethod() {
        MethodSpec.Builder methodBuilder = ProcessUtils.overrideMethod(mMethodElement);
        addCacheEvict(methodBuilder);
        addJsonObjectStatements(methodBuilder);
        addReadCacheStatements(methodBuilder);
        methodBuilder.beginControlFlow("if ($N == null)", mResultName);
        addStoreCacheAndReturnStatements(methodBuilder);
        methodBuilder.nextControlFlow("else");
        methodBuilder.addStatement("return $N", mResultName);
        methodBuilder.endControlFlow();
        return methodBuilder.build();
    }

    protected MethodSpec generateMethodPartA() {
        MethodSpec.Builder methodBuilder = copyMethodSignatureBuilder(mMethodElement, mMethodName + METHOD_PART_A_SUFFIX);
        addCacheEvict(methodBuilder);
        addJsonObjectStatements(methodBuilder);
        addReadCacheStatements(methodBuilder);
        methodBuilder.addStatement("return $N", mResultName);
        return methodBuilder.build();
    }

    protected MethodSpec generateMethodPartB() {
        MethodSpec.Builder methodBuilder = copyMethodSignatureBuilder(mMethodElement, mMethodName + METHOD_PART_B_SUFFIX);
        addJsonObjectStatements(methodBuilder);
        methodBuilder.addStatement("$T $N", mReturnType, mResultName);
        addStoreCacheAndReturnStatements(methodBuilder);
        return methodBuilder.build();
    }

    protected void addJsonObjectStatements(MethodSpec.Builder methodBuilder) {
        methodBuilder.addStatement("String $N = $S", mKeyName, mQualifiedName);
        intAdditionalParameters();
        int paramCount = parameterCount();
        if (paramCount == 0) {
            methodBuilder.addStatement("Object $N = null", mJsonObjectName);
        } else {
            ClassName jsonObjectTypeName = ClassName.get("com.alibaba.fastjson", "JSONObject");
            methodBuilder.addStatement("$T $N = new $T()", jsonObjectTypeName, mJsonObjectName, jsonObjectTypeName);
            for (VariableElement paramElement : mParameters) {
                String paramName = paramElement.getSimpleName().toString();
                methodBuilder.addStatement("$N.put($S, $N)", mJsonObjectName, paramName, paramName);
            }
            for (AdditionalParameter parameter : mAdditionalParameters) {
                String paramName = mClassName + "_" + parameter.getName();
                String statement = "$N.put($S, $N";
                if (parameter.isMethod()) {
                    statement += "())";
                } else {
                    statement += ")";
                }
                methodBuilder.addStatement(statement, mJsonObjectName, paramName, parameter.getName());
            }
        }
    }

    protected void addReadCacheStatements(MethodSpec.Builder methodBuilder) {
        String getMethodName;
        if (mReturnTypeForm == RETURN_TYPE_FORM_LIST) {
            getMethodName = "getList";
        } else if (mReturnTypeForm == RETURN_TYPE_FORM_ARRAY) {
            getMethodName = "getArray";
        } else {
            getMethodName = "getObject";
        }
        methodBuilder.addStatement("$T $N = $T.getInstance().$N($N, $N, $T.class)", mReturnType, mResultName, CACHE_STORAGE_TYPE,
                getMethodName, mKeyName, mJsonObjectName, mResultType);
    }

    private void addStoreCacheAndReturnStatements(MethodSpec.Builder methodBuilder) {
        methodBuilder.addStatement("$N = super.$N($N)", mResultName, mMethodName, buildParamNames());
        String indexableParamNames = buildIndexableParamNames();
        if (ProcessUtils.isStringEmpty(indexableParamNames)) {
            methodBuilder
                    .addStatement("$T.getInstance().put($N, $N, $N, $L, $S, $S)", CACHE_STORAGE_TYPE, mKeyName, mJsonObjectName,
                            mResultName, mExpire, mCacheAlias, indexableParamNames);
        } else {
            methodBuilder.addStatement("$T.getInstance().put($N, $N, $N, $L, $S, $S, $L)", CACHE_STORAGE_TYPE, mKeyName,
                    mJsonObjectName, mResultName, mExpire, mCacheAlias, indexableParamNames, indexableParamNames);
        }
        methodBuilder.addStatement("return $N", mResultName);
    }

}
