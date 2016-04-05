package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * Created by Allen on 2016/4/3.
 */
public abstract class AbstractMethod {

    public static final ClassName OBSERVABLE_TYPE = ClassName.get("rx", "Observable");

    public static final String SUBSCRIBER_NAME = "subscriber";

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

    public AbstractMethod(String packageName, String className, ExecutableElement methodElement) {
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

    protected MethodSpec.Builder observableMethod(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        String methodName = method.getSimpleName().toString();
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

        modifiers = new LinkedHashSet<>(modifiers);
        modifiers.remove(Modifier.ABSTRACT);
        methodBuilder.addModifiers(modifiers);

        for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

        initReturnType();

        TypeName returnType = ParameterizedTypeName.get(OBSERVABLE_TYPE, mReturnType);
        methodBuilder.returns(returnType);

        List<? extends VariableElement> parameters = method.getParameters();
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
        methodBuilder.varargs(method.isVarArgs());

        return methodBuilder;
    }

    public MethodSpec generateMethod(Map<String, Set<AdditionalParameter>> cacheParameters) {
        mCacheParameters = cacheParameters;
        MethodSpec.Builder methodBuilder = observableMethod(mMethodElement);
        TypeSpec.Builder onSubscribeBuilder = TypeSpec.anonymousClassBuilder("");
        ClassName onSubscribeTypeName = ClassName.get("rx", "Observable.OnSubscribe");
        onSubscribeBuilder.addSuperinterface(ParameterizedTypeName.get(onSubscribeTypeName, mReturnType));
        MethodSpec callMethod = generateCallMethod(getCallMethodBuilder());
        onSubscribeBuilder.addMethod(callMethod);
        methodBuilder.addStatement("return $T.create($L)", OBSERVABLE_TYPE, onSubscribeBuilder.build());
        return methodBuilder.build();
    }

    protected MethodSpec.Builder getCallMethodBuilder() {
        ParameterizedTypeName subscriberTypeName =
                ParameterizedTypeName.get(ClassName.get("rx", "Subscriber"), WildcardTypeName.supertypeOf(mReturnType));
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("call");
        methodBuilder.addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(subscriberTypeName, SUBSCRIBER_NAME);
        if (mExpireAlias != null) {
            String aliasName = mMethodName + "_a";
            methodBuilder.addStatement("String $N = $S", aliasName, mExpireAlias);
            methodBuilder.addStatement("$T.getInstance().remove($N)", CACHE_STORAGE_TYPE, aliasName);
        }
        return methodBuilder;
    }

    protected abstract MethodSpec generateCallMethod(MethodSpec.Builder methodBuilder);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheMethod that = (CacheMethod) o;

        if (mMethodName != null ? !mMethodName.equals(that.mMethodName) : that.mMethodName != null) {
            return false;
        }
        return mSignature != null ? mSignature.equals(that.mSignature) : that.mSignature == null;

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
