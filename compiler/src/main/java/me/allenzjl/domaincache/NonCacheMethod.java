package me.allenzjl.domaincache;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.ExecutableElement;

/**
 * Created by Allen on 2016/4/3.
 */
public class NonCacheMethod extends AbstractMethod {

    public NonCacheMethod(String packageName, String className, ExecutableElement methodElement) {
        super(packageName, className, methodElement);
    }

    @Override
    protected MethodSpec generateCallMethod(MethodSpec.Builder methodBuilder) {
        String resultName = mMethodName + "_r";
        String paramNames = buildParamNames();
        if (mReturnType != TypeName.VOID.box()) {
            methodBuilder
                    .addStatement("$T $N = $N.this.$N.$N($N)", mReturnType, resultName, mClassName, CacheClass.CLIENT_FIELD_NAME,
                            mMethodName, paramNames);
            methodBuilder.addStatement("$N.onNext($N)", SUBSCRIBER_NAME, resultName);
        } else {
            methodBuilder.addStatement("$N.this.$N.$N($N)", mClassName, CacheClass.CLIENT_FIELD_NAME, mMethodName, paramNames);
            methodBuilder.addStatement("$N.onNext(null)", SUBSCRIBER_NAME);
        }
        methodBuilder.addStatement("$N.onCompleted()", SUBSCRIBER_NAME);
        return methodBuilder.build();
    }

}
