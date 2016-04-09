package me.allenzjl.domaincache;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeVariable;

/**
 * The type Observable method.
 */
public class ObservableMethod extends BasicMethod {

    public static final ClassName OBSERVABLE_TYPE = ClassName.get("rx", "Observable");

    public static final String SUBSCRIBER_NAME = "subscriber";

    public static final ClassName CACHE_STORAGE_TYPE = ClassName.get("me.allenzjl.domaincache", "CacheStorage");

    protected CacheObservable mCacheObservable;

    protected int mCacheStrategy;

    protected boolean mAnnotatedByCacheable;

    protected ObservableMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
        processCacheObservableAnnotation();
    }

    protected void processCacheObservableAnnotation() {
        mCacheObservable = mMethodElement.getAnnotation(CacheObservable.class);
        int strategy = mCacheObservable.value();
        if (strategy != CacheStrategy.READ_CACHE_ONLY & strategy != CacheStrategy.PUSH_CACHE_FIRST) {
            ProcessUtils.printError("Value of attribute 'value' should be READ_CACHE_ONLY or PUSH_CACHE_FIRST", mMethodElement);
        }
        mCacheStrategy = strategy;
        mAnnotatedByCacheable = mMethodElement.getAnnotation(Cacheable.class) != null;
    }

    protected MethodSpec.Builder observableMethod(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        String methodName = method.getSimpleName().toString();
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

        methodBuilder.addModifiers(Modifier.PUBLIC);

        for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
            TypeVariable var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }

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

    protected MethodSpec.Builder getCallMethodBuilder() {
        ParameterizedTypeName subscriberTypeName =
                ParameterizedTypeName.get(ClassName.get("rx", "Subscriber"), WildcardTypeName.supertypeOf(mReturnType));
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("call");
        methodBuilder.addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(subscriberTypeName, SUBSCRIBER_NAME);
        return methodBuilder;
    }

    protected MethodSpec generateCallMethod(MethodSpec.Builder methodBuilder) {
        String resultName = mMethodName + "_r";
        String paramNames = buildParamNames();
        if (mAnnotatedByCacheable && mCacheStrategy == CacheStrategy.PUSH_CACHE_FIRST) {
            methodBuilder.addStatement("$T $N = $N.this.$N.$N($N)", mReturnType, resultName, mClassName,
                    ObservableClass.DOMAIN_PROXY_FIELD_NAME, mMethodName + CacheMethod.METHOD_PART_A_SUFFIX, paramNames);
            methodBuilder.beginControlFlow("if ($N != null)", resultName);
            methodBuilder.addStatement("$N.onNext($N)", SUBSCRIBER_NAME, resultName);
            methodBuilder.endControlFlow();
            methodBuilder.addStatement("$N = $N.this.$N.$N($N)", resultName, mClassName, ObservableClass.DOMAIN_PROXY_FIELD_NAME,
                    mMethodName + CacheMethod.METHOD_PART_B_SUFFIX, paramNames);
            methodBuilder.addStatement("$N.onNext($N)", SUBSCRIBER_NAME, resultName);
        } else {
            if (!mReturnType.equals(ClassName.get(Void.class))) {
                methodBuilder.addStatement("$T $N = $N.this.$N.$N($N)", mReturnType, resultName, mClassName,
                        ObservableClass.DOMAIN_PROXY_FIELD_NAME, mMethodName, paramNames);
                methodBuilder.addStatement("$N.onNext($N)", SUBSCRIBER_NAME, resultName);
            } else {
                methodBuilder.addStatement("$N.this.$N.$N($N)", mClassName, ObservableClass.DOMAIN_PROXY_FIELD_NAME, mMethodName,
                        paramNames);
                methodBuilder.addStatement("$N.onNext(null)", SUBSCRIBER_NAME);
            }
        }
        methodBuilder.addStatement("$N.onCompleted()", SUBSCRIBER_NAME);
        return methodBuilder.build();
    }

    @Override
    public List<MethodSpec> generateMethods() {
        MethodSpec.Builder methodBuilder = observableMethod(mMethodElement);
        TypeSpec.Builder onSubscribeBuilder = TypeSpec.anonymousClassBuilder("");
        ClassName onSubscribeTypeName = ClassName.get("rx", "Observable.OnSubscribe");
        onSubscribeBuilder.addSuperinterface(ParameterizedTypeName.get(onSubscribeTypeName, mReturnType));
        MethodSpec callMethod = generateCallMethod(getCallMethodBuilder());
        onSubscribeBuilder.addMethod(callMethod);
        methodBuilder.addStatement("return $T.create($L)", OBSERVABLE_TYPE, onSubscribeBuilder.build());

        List<MethodSpec> list = new ArrayList<>(1);
        list.add(methodBuilder.build());
        return list;
    }
}
